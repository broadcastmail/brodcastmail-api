package com.broadcastmail.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.resend")
public record ResendProperties(
        String webhookBaseUrl
) {
}
