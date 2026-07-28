package com.broadcastemail.api.campaign.dto;

import com.broadcastemail.api.campaign.filter.dto.FilterRequest;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CreateCampaignRequest(
        @NotBlank String name,
        @NotBlank String subject,
        @NotBlank String bodyHtml,
        @Future @Nullable OffsetDateTime scheduledAt,
        @NotNull UUID connectionId,
        List<@Valid FilterRequest> filters
) {}
