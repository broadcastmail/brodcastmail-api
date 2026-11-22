package com.broadcastmail.api.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// Fails startup instead of every account creation when RESEND_WEBHOOK_BASE_URL is missing.
@Validated
@ConfigurationProperties(prefix = "app.resend")
public record ResendProperties(
        @NotBlank @Pattern(regexp = "https?://\\S+", message = "must be an http(s) URL") String webhookBaseUrl
) {
}
