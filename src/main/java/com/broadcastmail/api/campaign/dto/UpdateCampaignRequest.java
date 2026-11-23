package com.broadcastmail.api.campaign.dto;

import com.broadcastmail.api.campaign.filter.dto.FilterRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Builder
public record UpdateCampaignRequest(

        // @Pattern rather than @NotBlank: a request that leaves the name out unwraps to null and must still pass.
        Optional<@Pattern(regexp = ".*\\S.*", message = "must not be blank") @Size(max = 120) String> name,
        Optional<String> subject,
        Optional<String> bodyHtml,
        Optional<OffsetDateTime> scheduledAt,
        List<@Valid FilterRequest> filters,
        String filterRootLogic,
        Optional<String> audienceMode,
        List<String> includedIds,
        List<String> excludedIds,
        UUID excludedCampaignId
) {}
