package com.broadcastmail.api.campaign.dto;


import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignStatus;

import com.broadcastmail.common.campaign.filter.CampaignFilter;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
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
        OffsetDateTime updatedAt,
        List<CampaignFilterResponse> filters
) {
    /** For campaigns whose filters aren't relevant to the caller (e.g. retry campaigns). */
    public static CampaignResponse from(Campaign campaign) {
        return from(campaign, List.of());
    }

    /** Filters are returned in their saved order. */
    public static CampaignResponse from(Campaign campaign, List<CampaignFilter> filters) {
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
                campaign.getUpdatedAt(),
                filters.stream()
                        .sorted(Comparator.comparingInt(CampaignFilter::getFilterOrder))
                        .map(CampaignFilterResponse::from)
                        .toList()
        );
    }
}
