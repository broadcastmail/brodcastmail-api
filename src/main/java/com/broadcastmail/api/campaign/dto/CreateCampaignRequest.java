package com.broadcastmail.api.campaign.dto;

import com.broadcastmail.api.campaign.filter.dto.FilterRequest;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;
import java.util.List;

public record CreateCampaignRequest(
        @NotBlank String name,
        @NotBlank String subject,
        @NotBlank String bodyHtml,
        @Future @Nullable OffsetDateTime scheduledAt,
        List<@Valid FilterRequest> filters
) {}
