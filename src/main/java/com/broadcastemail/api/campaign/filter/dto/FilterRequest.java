package com.broadcastemail.api.campaign.filter.dto;

import com.broadcastemail.api.campaign.filter.FilterOperator;
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
