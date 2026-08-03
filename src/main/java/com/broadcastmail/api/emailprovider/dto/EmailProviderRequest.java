package com.broadcastmail.api.emailprovider.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailProviderRequest(
        @NotBlank String apiKey,
        @NotBlank @Email String fromAddress
) {}
