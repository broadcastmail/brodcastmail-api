package com.broadcastmail.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String unsubscribeSecret,
        Frontend frontend
) {
    public record Frontend(String url, List<String> allowedOrigins, String oauthCallbackPath) {}
}
