package com.broadcastmail.api.campaign.dto;

public record RecipientPreviewResponse(
        int recipientCount,
        long usedThisPeriod,
        int limit
) {
}
