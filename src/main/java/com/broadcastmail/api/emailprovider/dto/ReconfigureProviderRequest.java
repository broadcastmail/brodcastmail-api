package com.broadcastmail.api.emailprovider.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ReconfigureProviderRequest(
        @NotBlank String apiKey,
        String fromName,
        @NotBlank @Email String fromAddress
) {}
