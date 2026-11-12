package com.broadcastmail.api.campaign.history.projection;

import com.broadcastmail.api.common.LowerCaseEnumSerializer;
import com.broadcastmail.common.campaign.recipient.RecipientStatus;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RecipientHistoryResponse(
        UUID campaignId,
        String campaignName,
        OffsetDateTime campaignSentAt,
        String email,
        String externalUserId,
        @JsonSerialize(using = LowerCaseEnumSerializer.class) RecipientStatus status
) {}
