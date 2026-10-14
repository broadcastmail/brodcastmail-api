package com.broadcastmail.api.campaign.dto;

import com.broadcastmail.api.common.LowerCaseEnumSerializer;
import com.broadcastmail.common.campaign.recipient.CampaignRecipient;
import com.broadcastmail.common.campaign.recipient.RecipientStatus;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RecipientResponse(
        UUID id,
        String email,
        @JsonSerialize(using = LowerCaseEnumSerializer.class) RecipientStatus status,
        OffsetDateTime sentAt,
        OffsetDateTime deliveredAt,
        OffsetDateTime openedAt
) {
    public static RecipientResponse from(CampaignRecipient r) {
        return new RecipientResponse(r.getId(), r.getEmail(), r.getStatus(),
                r.getSentAt(), r.getDeliveredAt(), r.getOpenedAt());
    }
}
