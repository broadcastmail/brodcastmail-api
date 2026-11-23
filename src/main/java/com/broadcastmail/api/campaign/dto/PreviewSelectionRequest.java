package com.broadcastmail.api.campaign.dto;

import com.broadcastmail.api.campaign.filter.dto.FilterRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record PreviewSelectionRequest(
        @Valid List<FilterRequest> filters,
        String filterRootLogic,
        String audienceMode,
        @Size(max = 5000) List<String> includedIds,
        List<String> excludedIds,
        UUID excludedCampaignId
) {
}
