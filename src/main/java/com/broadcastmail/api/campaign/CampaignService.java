package com.broadcastmail.api.campaign;

import com.broadcastmail.api.campaign.dto.CreateCampaignRequest;
import com.broadcastmail.api.campaign.dto.RecipientResponse;
import com.broadcastmail.api.campaign.dto.UpdateCampaignRequest;
import com.broadcastmail.common.campaign.AudienceMode;
import com.broadcastmail.common.campaign.filter.CampaignFilter;
import com.broadcastmail.common.campaign.filter.CampaignFilterRepository;
import com.broadcastmail.api.campaign.filter.CampaignFilterValidator;
import com.broadcastmail.api.campaign.filter.dto.FilterRequest;
import com.broadcastmail.api.common.exceptions.CampaignNotEditableException;
import com.broadcastmail.api.common.exceptions.CampaignNotFoundException;
import com.broadcastmail.api.common.exceptions.ConnectionNotFoundException;
import com.broadcastmail.api.common.exceptions.InvalidCampaignFilterException;
import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.campaign.CampaignStatus;
import com.broadcastmail.common.campaign.recipient.CampaignRecipientRepository;
import com.broadcastmail.common.campaign.recipient.RecipientStatus;
import com.broadcastmail.common.connection.ConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CampaignService {
    private final CampaignRepository campaignRepository;
    private final CampaignFilterRepository filterRepository;
    private final CampaignFilterValidator filterValidator;
    private final ConnectionRepository connectionRepository;
    private final CampaignRecipientRepository recipientRepository;


    @Transactional
    public Campaign createCampaign(UUID accountId, CreateCampaignRequest request) {
        UUID connectionId = connectionRepository.findByAccountId(accountId)
                .orElseThrow(ConnectionNotFoundException::new)
                .getId();
        filterValidator.validateRequests(accountId, request.filters());
        Campaign campaign = mapToEntity(accountId, connectionId, request);
        Campaign saved = campaignRepository.save(campaign);
        List<CampaignFilter> filters = new ArrayList<>();

        if (request.filters() != null) {
            for (int i = 0; i < request.filters().size(); i++) {
                filters.add(mapToFilter(saved.getId(), i, request.filters().get(i)));
            }
            filterRepository.saveAll(filters);
        }
        return saved;
    }

    public Campaign getCampaign(UUID accountId, UUID campaignId) {
        return campaignRepository.findByAccountIdAndId(accountId, campaignId)
                .orElseThrow(() -> new CampaignNotFoundException(campaignId));
    }

    public List<CampaignFilter> getFilters(UUID campaignId) {
        return filterRepository.findByCampaignId(campaignId);
    }

    public Page<Campaign> listCampaigns(UUID accountId, Pageable pageable) {
        return campaignRepository.findByAccountId(accountId, pageable);
    }

    @Transactional
    public Campaign updateCampaign(UUID accountId, UUID campaignId, UpdateCampaignRequest request) {
        Campaign campaign = getCampaign(accountId, campaignId);

        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new CampaignNotEditableException();
        }


        request.name().ifPresent(campaign::setName);
        request.subject().ifPresent(campaign::setSubject);
        request.bodyHtml().ifPresent(html -> campaign.setBodyHtml(sanitize(html)));
        request.scheduledAt().ifPresent(campaign::setScheduledAt);

        request.audienceMode().ifPresent(raw -> {
            try {
                campaign.setAudienceMode(AudienceMode.valueOf(raw.toUpperCase()));
            } catch (IllegalArgumentException _) {
                throw new InvalidCampaignFilterException(List.of("Invalid audienceMode: " + raw));
            }
        });

        if (request.includedIds() != null) {
            if (request.includedIds().size() > 5000) {
                throw new InvalidCampaignFilterException(List.of("includedIds exceeds the 5,000 limit"));
            }
            campaign.setIncludedIds(request.includedIds());
        }

        if (request.excludedIds() != null) {
            campaign.setExcludedIds(request.excludedIds());
        }

        if (request.filters() != null) {
            filterValidator.validateRequests(accountId, request.filters());
            filterRepository.deleteAll(filterRepository.findByCampaignId(campaignId));
            filterRepository.flush();
            List<CampaignFilter> filters = new ArrayList<>();
            for (int i = 0; i < request.filters().size(); i++) {
                filters.add(mapToFilter(campaignId, i, request.filters().get(i)));
            }
            filterRepository.saveAll(filters);
        }

        return campaignRepository.save(campaign);
    }

    public void deleteCampaign(UUID accountId, UUID campaignId) {
        Campaign campaign = getCampaign(accountId, campaignId);
        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new CampaignNotEditableException();
        }
        campaignRepository.deleteById(campaign.getId());
    }

    public Page<RecipientResponse> getCampaignRecipients(UUID accountId, UUID campaignId, String status, Pageable pageable) {
        getCampaign(accountId, campaignId);
        if (status == null) {
            return recipientRepository.findByCampaignId(campaignId, pageable).map(RecipientResponse::from);
        }
        RecipientStatus recipientStatus = RecipientStatus.valueOf(status.toUpperCase());
        if (recipientStatus == RecipientStatus.DELIVERED) {
            return recipientRepository.findByCampaignIdAndStatusIn(
                    campaignId, List.of(RecipientStatus.DELIVERED, RecipientStatus.OPENED), pageable)
                    .map(RecipientResponse::from);
        }
        return recipientRepository.findByCampaignIdAndStatus(campaignId, recipientStatus, pageable)
                .map(RecipientResponse::from);
    }

    private Campaign mapToEntity(UUID accountId, UUID connectionId, CreateCampaignRequest request) {
        return Campaign.builder()
                .accountId(accountId)
                .name(request.name())
                .subject(request.subject())
                .bodyHtml(sanitize(request.bodyHtml()))
                .status(CampaignStatus.DRAFT)
                .connectionId(connectionId)
                .scheduledAt(request.scheduledAt())
                .build();
    }

    private String sanitize(String html) {
        return Jsoup.clean(html, Safelist.relaxed().addAttributes(":all", "style"));
    }

    private CampaignFilter mapToFilter(UUID campaignId, int order, FilterRequest request) {
        return CampaignFilter.builder()
                .campaignId(campaignId)
                .columnName(request.columnName())
                .operator(request.operator())
                .filterValue(request.filterValue())
                .filterOrder(order)
                .source(request.sourceOrDefault())
                .jsonKey(request.jsonKey())
                .build();
    }
}
