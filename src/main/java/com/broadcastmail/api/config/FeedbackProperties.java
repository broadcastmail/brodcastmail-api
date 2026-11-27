package com.broadcastmail.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "app.feedback")
public record FeedbackProperties(
        String resendApiKey,
        String from,
        String to,
        String turnstileSecret
) {

    public boolean enabled() {
        return isSet(resendApiKey) && isSet(from) && isSet(to);
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
