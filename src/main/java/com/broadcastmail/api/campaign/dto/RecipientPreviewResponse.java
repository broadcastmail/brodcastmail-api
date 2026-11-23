package com.broadcastmail.api.campaign.dto;

public record RecipientPreviewResponse(
        int recipientCount,
        int newRecipientCount,
        long usedThisPeriod,
        int limit
) {
}
