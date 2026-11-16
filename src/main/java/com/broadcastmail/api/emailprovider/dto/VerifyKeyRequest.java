package com.broadcastmail.api.emailprovider.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyKeyRequest(
        @NotBlank String apiKey
) {}
