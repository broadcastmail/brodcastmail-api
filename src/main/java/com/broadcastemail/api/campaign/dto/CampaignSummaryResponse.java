package com.broadcastemail.api.campaign.dto;

import com.broadcastemail.api.campaign.Campaign;
import com.broadcastemail.api.campaign.CampaignStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CampaignSummaryResponse(
        UUID id,
        String name,
        CampaignStatus status,
        Integer recipientCount,
        OffsetDateTime scheduledAt,
        OffsetDateTime sentAt,
        OffsetDateTime createdAt
) {
    public static CampaignSummaryResponse from(Campaign campaign) {
        return new CampaignSummaryResponse(
                campaign.getId(),
                campaign.getName(),
                campaign.getStatus(),
                campaign.getRecipientCount(),
                campaign.getScheduledAt(),
                campaign.getSentAt(),
                campaign.getCreatedAt()
        );
    }
}
