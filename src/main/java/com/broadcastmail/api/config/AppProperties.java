package com.broadcastmail.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String unsubscribeSecret,
        Frontend frontend
) {

    public record Frontend(List<String> allowedOrigins, String oauthCallbackPath, String unsubscribeRedirectUrl,
                           String cookieDomain) {

        /** {@code null} when unset, so {@code ResponseCookie.domain(...)} omits the attribute. */
        public String cookieDomainOrNull() {
            return cookieDomain == null || cookieDomain.isBlank() ? null : cookieDomain;
        }
    }
}
