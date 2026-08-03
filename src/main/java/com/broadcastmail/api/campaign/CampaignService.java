package com.broadcastmail.api.campaign;

import com.broadcastmail.api.campaign.dto.CreateCampaignRequest;
import com.broadcastmail.api.campaign.dto.UpdateCampaignRequest;
import com.broadcastmail.api.campaign.filter.CampaignFilter;
import com.broadcastmail.api.campaign.filter.CampaignFilterRepository;
import com.broadcastmail.api.campaign.filter.dto.FilterRequest;
import com.broadcastmail.api.common.exceptions.CampaignNotEditableException;
import com.broadcastmail.api.common.exceptions.CampaignNotFoundException;
import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.campaign.CampaignStatus;
import lombok.RequiredArgsConstructor;
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


    @Transactional
    public Campaign createCampaign(UUID accountId, CreateCampaignRequest request)
    {
        Campaign campaign = mapToEntity(accountId, request);
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
                .orElseThrow(CampaignNotFoundException::new);
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
        request.bodyHtml().ifPresent(campaign::setBodyHtml);
        request.scheduledAt().ifPresent(campaign::setScheduledAt);

        return campaignRepository.save(campaign);
    }

    public void deleteCampaign(UUID accountId, UUID campaignId) {
        Campaign campaign = getCampaign(accountId, campaignId);
        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new CampaignNotEditableException();
        }
        campaignRepository.deleteById(campaign.getId());
    }

    private Campaign mapToEntity(UUID accountId, CreateCampaignRequest request)
    {
        return Campaign.builder()
                .accountId(accountId)
                .name(request.name())
                .subject(request.subject())
                .bodyHtml(request.bodyHtml())
                .status(CampaignStatus.DRAFT)
                .connectionId(request.connectionId())
                .scheduledAt(request.scheduledAt())
                .build();
    }

    private CampaignFilter mapToFilter(UUID campaignId, int order, FilterRequest request) {
        return CampaignFilter.builder()
                .campaignId(campaignId)
                .columnName(request.columnName())
                .operator(request.operator())
                .filterValue(request.filterValue())
                .filterOrder(order)
                .build();
    }
}
