package com.broadcastmail.api.campaign.filter.dto;

import com.broadcastmail.common.campaign.filter.FilterOperator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FilterRequest(
        @NotBlank
        String columnName,
        @NotNull
        FilterOperator operator,
        @NotBlank
        String filterValue
) {
}
