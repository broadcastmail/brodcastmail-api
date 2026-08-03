package com.broadcastmail.api.campaign.dto;


import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CampaignResponse(
        UUID id,
        UUID connectionId,
        String name,
        String subject,
        String bodyHtml,
        CampaignStatus status,
        Integer recipientCount,
        Integer sentCount,
        Integer deliveredCount,
        Integer openedCount,
        Integer bouncedCount,
        Integer failedCount,
        OffsetDateTime scheduledAt,
        OffsetDateTime sentAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static CampaignResponse from(Campaign campaign) {
        return new CampaignResponse(
                campaign.getId(),
                campaign.getConnectionId(),
                campaign.getName(),
                campaign.getSubject(),
                campaign.getBodyHtml(),
                campaign.getStatus(),
                campaign.getRecipientCount(),
                campaign.getSentCount(),
                campaign.getDeliveredCount(),
                campaign.getOpenedCount(),
                campaign.getBouncedCount(),
                campaign.getFailedCount(),
                campaign.getScheduledAt(),
                campaign.getSentAt(),
                campaign.getCreatedAt(),
                campaign.getUpdatedAt()
        );
    }
}
