package com.broadcastmail.api.campaign;

import com.broadcastmail.api.campaign.dto.RecipientPreviewResponse;
import com.broadcastmail.api.campaign.filter.CampaignFilterValidator;
import com.broadcastmail.common.campaign.filter.CampaignFilter;
import com.broadcastmail.common.campaign.filter.CampaignFilterRepository;
import com.broadcastmail.api.common.exceptions.CampaignNotFoundException;
import com.broadcastmail.api.common.exceptions.ConnectionNotFoundException;
import com.broadcastmail.common.campaign.filter.CampaignFilterSerializer;
import com.broadcastmail.common.campaign.filter.FilterQuery;
import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import com.broadcastmail.api.audience.TenantAudienceGateway;
import com.broadcastmail.api.campaign.dto.PreviewSelectionRequest;
import com.broadcastmail.api.campaign.filter.FilterRequestMapper;
import com.broadcastmail.api.campaign.filter.dto.FilterRequest;
import com.broadcastmail.api.common.exceptions.AccountNotFoundException;
import com.broadcastmail.api.common.exceptions.InvalidCampaignFilterException;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.campaign.AudienceMode;
import com.broadcastmail.common.connection.RecipientSource;
import com.broadcastmail.common.account.AccountRepository;
import com.broadcastmail.common.campaign.recipient.CampaignRecipientRepository;
import com.broadcastmail.common.usage.RecipientUsageRepository;
import com.broadcastmail.common.suppressions.Suppression;
import com.broadcastmail.common.suppressions.SuppressionsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecipientPreviewService {

    private static final String WHERE = "WHERE ";
    private static final int PLAN_PERIOD_DAYS = 30;

    // The repository, not CampaignService: CampaignService asks this class how many picked users are new.
    private final CampaignRepository campaignRepository;
    private final ConnectionRepository connectionRepository;
    private final TenantAudienceGateway tenantAudienceGateway;
    private final CampaignFilterRepository filterRepository;
    private final CampaignFilterSerializer filterSerializer;
    private final CampaignFilterValidator filterValidator;
    private final SuppressionsRepository suppressionsRepository;
    private final CampaignRecipientRepository recipientRepository;
    private final RecipientUsageRepository recipientUsageRepository;
    private final AccountRepository accountRepository;

    /** What gets counted: the same rules the worker applies when it resolves who to send to. */
    public record AudienceSelection(List<CampaignFilter> filters, String rootLogic, AudienceMode mode,
                                    List<String> includedIds, List<String> excludedIds, UUID excludedCampaignId) {}

    public RecipientPreviewResponse preview(UUID accountId, UUID campaignId) {
        Campaign campaign = campaign(accountId, campaignId);
        Connection connection = connectionRepository.findByAccountId(accountId)
                .orElseThrow(() -> new CampaignNotFoundException(campaignId));

        List<CampaignFilter> filters = filterRepository.findByCampaignId(campaignId);
        filterValidator.validateSaved(accountId, filters);
        AudienceSelection selection = new AudienceSelection(filters, campaign.getFilterRootLogic(), campaign.getAudienceMode(),
                campaign.getIncludedIds(), campaign.getExcludedIds(), campaign.getExcludedCampaignId());
        return countAndQuota(accountId, connection, selection, true);
    }

    public RecipientPreviewResponse previewSelection(UUID accountId, UUID campaignId, PreviewSelectionRequest request) {
        campaign(accountId, campaignId);
        Account account = accountRepository.findById(accountId).orElseThrow(AccountNotFoundException::new);
        Connection connection = connectionRepository.findByAccountId(accountId)
                .orElseThrow(() -> new CampaignNotFoundException(campaignId));
        if (request.excludedCampaignId() != null) campaign(accountId, request.excludedCampaignId());

        List<FilterRequest> filters = FilterRequestMapper.withValues(request.filters() != null ? request.filters() : List.of());
        filterValidator.validateRequests(account, filters);
        AudienceSelection selection = new AudienceSelection(FilterRequestMapper.toEntities(filters), request.filterRootLogic(),
                parseMode(request.audienceMode()), request.includedIds(), request.excludedIds(), request.excludedCampaignId());
        return countAndQuota(accountId, connection, selection, false);
    }

    /**
     * How many of the campaign's picked users (includedIds) haven't been emailed this plan period, after its
     * exclusions and suppressions: the number the plan limit counts, the same way confirm counts it.
     */
    public int newManualRecipients(UUID accountId, Campaign campaign) {
        Connection connection = connectionRepository.findByAccountId(accountId)
                .orElseThrow(ConnectionNotFoundException::new);
        AudienceSelection selection = new AudienceSelection(List.of(), null, AudienceMode.MANUAL,
                campaign.getIncludedIds(), campaign.getExcludedIds(), campaign.getExcludedCampaignId());
        return countAndQuota(accountId, connection, selection, false).newRecipientCount();
    }

    private Campaign campaign(UUID accountId, UUID campaignId) {
        return campaignRepository.findByAccountIdAndId(accountId, campaignId)
                .orElseThrow(() -> new CampaignNotFoundException(campaignId));
    }

    private RecipientPreviewResponse countAndQuota(UUID accountId, Connection connection, AudienceSelection selection,
                                                   boolean persistEstimate) {
        Set<String> usedThisPeriod = new HashSet<>(recipientUsageRepository.findEmailsSince(
                accountId, OffsetDateTime.now(ZoneId.systemDefault()).minusDays(PLAN_PERIOD_DAYS)));
        try {
            TenantAudienceGateway.Counts counts = count(accountId, connection, selection, usedThisPeriod);
            if (persistEstimate) {
                connection.setEstimatedUserCount(counts.total());
                connectionRepository.save(connection);
            }
            int limit = accountRepository.findById(accountId)
                    .map(a -> a.getPlan().strategy().recipientLimit())
                    .orElse(0);
            return new RecipientPreviewResponse(counts.total(), counts.fresh(), usedThisPeriod.size(), limit);
        } catch (SQLException e) {
            throw new RuntimeException("Recipient preview failed: " + e.getMessage(), e);
        }
    }

    private TenantAudienceGateway.Counts count(UUID accountId, Connection connection, AudienceSelection selection,
                                               Set<String> usedThisPeriod) throws SQLException {
        RecipientSource source = RecipientSource.of(connection);
        List<String> conditions = new ArrayList<>(List.of(source.emailExpr() + " IS NOT NULL"));
        List<Object> params = new ArrayList<>();
        boolean joinProfile = false;

        if (selection.mode() == AudienceMode.MANUAL) {
            List<String> included = selection.includedIds() != null ? selection.includedIds() : List.of();
            if (included.isEmpty()) return new TenantAudienceGateway.Counts(0, 0);
            conditions.add(source.idExpr() + "::text = ANY(?)");
            params.add(included.toArray(String[]::new));
        } else {
            String rootLogic = "OR".equalsIgnoreCase(selection.rootLogic()) ? "OR" : "AND";
            FilterQuery filterQuery = filterSerializer.serialize(selection.filters(), rootLogic, source.filterAliases());
            if (!filterQuery.sql().isEmpty()) {
                conditions.add("(" + filterQuery.sql().substring(WHERE.length()) + ")");
                params.addAll(filterQuery.parameters());
                joinProfile = true;
            }
        }

        if (selection.excludedIds() != null && !selection.excludedIds().isEmpty()) {
            conditions.add(source.idExpr() + "::text <> ALL(?)");
            params.add(selection.excludedIds().toArray(String[]::new));
        }
        Set<String> excludedEmails = excludedEmails(accountId, selection.excludedCampaignId());
        if (!excludedEmails.isEmpty()) {
            conditions.add(source.emailExpr() + " <> ALL(?)");
            params.add(excludedEmails.toArray(String[]::new));
        }

        String fresh = "COUNT(*)";
        if (!usedThisPeriod.isEmpty()) {
            fresh = "COUNT(*) FILTER (WHERE " + source.emailExpr() + " <> ALL(?))";
            params.addFirst(usedThisPeriod.toArray(String[]::new));
        }
        String sql = "SELECT COUNT(*), " + fresh + " FROM " + source.from(joinProfile)
                + " WHERE " + String.join(" AND ", conditions);
        return tenantAudienceGateway.count(connection, sql, params);
    }


    private Set<String> excludedEmails(UUID accountId, UUID excludedCampaignId) {
        Set<String> excluded = new LinkedHashSet<>();
        suppressionsRepository.findByAccountId(accountId).stream()
                .map(Suppression::getEmail)
                .forEach(excluded::add);
        if (excludedCampaignId != null) {
            excluded.addAll(recipientRepository.findDistinctEmailsByCampaignId(excludedCampaignId));
        }
        return excluded;
    }

    private static AudienceMode parseMode(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return AudienceMode.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException _) {
            throw new InvalidCampaignFilterException(List.of("Invalid audienceMode: " + raw));
        }
    }
}
