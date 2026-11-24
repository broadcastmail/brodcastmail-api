package com.broadcastmail.api.campaign.dto;

import com.broadcastmail.api.campaign.filter.dto.FilterRequest;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

public record CreateCampaignRequest(
        @NotBlank String name,
        @NotNull String subject,
        @NotNull String bodyHtml,
        @Nullable @Size(max = 255) String previewText,
        @Future @Nullable OffsetDateTime scheduledAt,
        List<@Valid FilterRequest> filters,
        String filterRootLogic
) {}
