package com.broadcastmail.api.campaign.dto;

import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignStatus;

public record CampaignStatusEvent(
        CampaignStatus status,
        Integer recipientsCount,
        Integer sentCount,
        Integer openedCount,
        Integer deliveredCount,
        Integer bouncedCount,
        Integer failedCount
) {
    public static CampaignStatusEvent from(Campaign campaign) {
        return new CampaignStatusEvent(
                campaign.getStatus(),
                campaign.getRecipientCount(),
                campaign.getSentCount(),
                campaign.getOpenedCount(),
                campaign.getDeliveredCount(),
                campaign.getBouncedCount(),
                campaign.getFailedCount()
        );
    }
}
