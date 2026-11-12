package com.broadcastmail.api.campaign;

import com.broadcastmail.api.campaign.filter.CampaignFilterValidator;
import com.broadcastmail.common.campaign.filter.CampaignFilter;
import com.broadcastmail.common.campaign.filter.CampaignFilterRepository;
import com.broadcastmail.api.common.exceptions.CampaignNotFoundException;
import com.broadcastmail.common.campaign.filter.CampaignFilterSerializer;
import com.broadcastmail.common.campaign.filter.FilterQuery;
import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import com.broadcastmail.api.audience.TenantAudienceGateway;
import com.broadcastmail.api.supabase.ConnectionQueryContext;
import com.broadcastmail.common.campaign.recipient.CampaignRecipientRepository;
import com.broadcastmail.common.suppressions.Suppression;
import com.broadcastmail.common.suppressions.SuppressionsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecipientPreviewService {

    private final CampaignService campaignService;
    private final ConnectionRepository connectionRepository;
    private final TenantAudienceGateway tenantAudienceGateway;
    private final CampaignFilterRepository filterRepository;
    private final CampaignFilterSerializer filterSerializer;
    private final CampaignFilterValidator filterValidator;
    private final SuppressionsRepository suppressionsRepository;
    private final CampaignRecipientRepository recipientRepository;

    public Integer preview(UUID accountId, UUID campaignId) {
        Campaign campaign = campaignService.getCampaign(accountId, campaignId);
        Connection connection = connectionRepository.findByAccountId(accountId)
                .orElseThrow(() -> new CampaignNotFoundException(campaignId));

        List<CampaignFilter> filters = filterRepository.findByCampaignId(campaignId);
        filterValidator.validateSaved(accountId, filters);
        String rootLogic = campaign.getFilterRootLogic() != null ? campaign.getFilterRootLogic() : "AND";
        FilterQuery filterQuery = filterSerializer.serialize(filters, rootLogic);

        Set<String> allExcluded = buildExclusionSet(accountId, campaign);
        boolean hasFilters = !filterQuery.sql().isEmpty();
        boolean hasExcluded = !allExcluded.isEmpty();

        String sql = buildCountSql(connection, filterQuery, hasFilters, hasExcluded);

        try {
            int count = tenantAudienceGateway.countRecipients(connection, sql, filterQuery.parameters(), allExcluded);
            connection.setEstimatedUserCount(count);
            connectionRepository.save(connection);
            return count;
        } catch (SQLException e) {
            throw new RuntimeException("Recipient preview failed: " + e.getMessage(), e);
        }
    }

    private Set<String> buildExclusionSet(UUID accountId, Campaign campaign) {
        Set<String> excluded = new LinkedHashSet<>();

        suppressionsRepository.findByAccountId(accountId).stream()
                .map(Suppression::getEmail)
                .forEach(excluded::add);

        if (campaign.getExcludedIds() != null && !campaign.getExcludedIds().isEmpty()) {
            excluded.addAll(recipientRepository.findDistinctEmailsByExternalUserIds(accountId, campaign.getExcludedIds()));
        }
        if (campaign.getExcludedCampaignId() != null) {
            excluded.addAll(recipientRepository.findDistinctEmailsByCampaignId(campaign.getExcludedCampaignId()));
        }

        return excluded;
    }

    private String buildCountSql(Connection connection, FilterQuery filterQuery,
                                  boolean hasFilters, boolean hasExcluded) {
        ConnectionQueryContext ctx = ConnectionQueryContext.from(connection);
        if (!hasFilters) {
            String base = ctx.buildBaseCountQuery();
            return hasExcluded ? base + " WHERE " + ctx.emailColumnExpression() + " != ALL(?)" : base;
        }
        String base = ctx.buildRecipientCountQuery() + " " + filterQuery.sql();
        return hasExcluded ? base + " AND " + ctx.emailColumnExpression() + " != ALL(?)" : base;
    }
}
