package com.broadcastmail.api.campaign.dto;

import com.broadcastmail.api.campaign.filter.dto.FilterRequest;
import jakarta.validation.Valid;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Builder
public record UpdateCampaignRequest(

        Optional<String> name,
        Optional<String> subject,
        Optional<String> bodyHtml,
        Optional<OffsetDateTime> scheduledAt,
        List<@Valid FilterRequest> filters,
        Optional<String> audienceMode,
        List<String> includedIds,
        List<String> excludedIds
) {}
