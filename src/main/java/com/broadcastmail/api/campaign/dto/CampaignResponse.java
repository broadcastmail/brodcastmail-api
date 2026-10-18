package com.broadcastmail.api.campaign.dto;


import com.broadcastmail.api.common.LowerCaseEnumSerializer;
import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignStatus;
import com.broadcastmail.common.campaign.AudienceMode;
import com.broadcastmail.common.campaign.filter.CampaignFilter;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record CampaignResponse(
        @NotNull UUID id,
        @NotNull UUID connectionId,
        UUID retryOfCampaignId,
        @NotNull String name,
        @NotNull String subject,
        @NotNull String bodyHtml,
        @NotNull CampaignStatus status,
        Integer recipientCount,
        @NotNull Integer sentCount,
        @NotNull Integer deliveredCount,
        @NotNull Integer openedCount,
        @NotNull Integer bouncedCount,
        @NotNull Integer failedCount,
        OffsetDateTime scheduledAt,
        OffsetDateTime sentAt,
        @NotNull OffsetDateTime createdAt,
        @NotNull OffsetDateTime updatedAt,
        @NotNull List<CampaignFilterResponse> filters,
        @NotNull @JsonSerialize(using = LowerCaseEnumSerializer.class) AudienceMode audienceMode,
        List<String> includedIds,
        List<String> excludedIds
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
                campaign.getRetryOfCampaignId(),
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
                        .toList(),
                campaign.getAudienceMode(),
                campaign.getIncludedIds(),
                campaign.getExcludedIds()
        );
    }
}
