package com.broadcastmail.api.campaign.dto;

import com.broadcastmail.common.campaign.recipient.CampaignRecipient;
import com.broadcastmail.common.campaign.recipient.RecipientStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RecipientResponse(
        UUID id,
        UUID campaignId,
        String email,
        RecipientStatus status,
        String failedReason,
        OffsetDateTime sentAt,
        OffsetDateTime deliveredAt,
        OffsetDateTime openedAt
) {
    public static RecipientResponse from(CampaignRecipient r) {
        return new RecipientResponse(
                r.getId(),
                r.getCampaignId(),
                r.getEmail(),
                r.getStatus(),
                r.getFailedReason(),
                r.getSentAt(),
                r.getDeliveredAt(),
                r.getOpenedAt()
        );
    }
}
