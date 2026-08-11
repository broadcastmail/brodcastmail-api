package com.broadcastmail.api.campaign.confirm;

import com.broadcastmail.api.campaign.CampaignService;
import com.broadcastmail.api.common.exceptions.CampaignNotEditableException;
import com.broadcastmail.api.common.exceptions.ConnectionNotFoundException;
import com.broadcastmail.common.connection.ConnectionRepository;
import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.campaign.CampaignStatus;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CampaignConfirmService {

    private final CampaignService campaignService;
    private final CampaignRepository campaignRepository;
    private final ConnectionRepository connectionRepository;

    @Transactional
    public void confirmCampaign(UUID accountId, UUID campaignId) {
        Campaign campaign = campaignService.getCampaign(accountId, campaignId);
        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new CampaignNotEditableException();
        }

        connectionRepository.findByAccountId(accountId)
                .orElseThrow(ConnectionNotFoundException::new);

        campaign.setStatus(CampaignStatus.RESOLVING);
        campaignRepository.save(campaign);
    }
}