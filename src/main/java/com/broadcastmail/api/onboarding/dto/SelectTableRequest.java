package com.broadcastmail.api.onboarding.dto;

import jakarta.validation.constraints.NotBlank;

public record SelectTableRequest(
        @NotBlank String userTableSchema,
        @NotBlank String userTableName
) {
}
