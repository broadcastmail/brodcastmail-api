package com.broadcastmail.api.auth;

import com.broadcastmail.api.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class CookieService {
    private final AppProperties appProperties;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;
    public static final String SESSION_COOKIE = "bm_session";

    private String cookieDomain() {
        return appProperties.frontend().cookieDomainOrNull();
    }

    private boolean isProduction() {
        return "prod".equals(activeProfile);
    }

    public ResponseCookie createSessionCookie(String sessionToken, Duration maxAge) {
        return ResponseCookie.from(SESSION_COOKIE, sessionToken)
                .domain(cookieDomain())
                .httpOnly(true)
                .secure(isProduction())
                .sameSite(isProduction() ? "None" : "Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    public ResponseCookie clearSessionCookie() {
        return ResponseCookie.from(SESSION_COOKIE, "")
                .domain(cookieDomain())
                .httpOnly(true)
                .secure(isProduction())
                .sameSite(isProduction() ? "None" : "Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }

    public ResponseCookie createOnboardingCookie(String sessionToken) {
        return ResponseCookie.from("onboarding_session", sessionToken)
                .domain(cookieDomain())
                .httpOnly(true)
                .secure(isProduction())
                .sameSite("Lax")
                .maxAge(Duration.ofMinutes(30))
                .path("/")
                .build();
    }

    public ResponseCookie clearOnboardingCookie() {
        return ResponseCookie.from("onboarding_session", "")
                .domain(cookieDomain())
                .httpOnly(true)
                .secure(isProduction())
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .path("/")
                .build();
    }

    public ResponseCookie createPkceOriginCookie(String origin) {
        return ResponseCookie.from("oauth_redirect_origin", origin)
                .httpOnly(true)
                .secure(isProduction())
                .sameSite("Lax")
                .maxAge(Duration.ofMinutes(5))
                .path(appProperties.frontend().oauthCallbackPath())
                .build();
    }

    public ResponseCookie clearPkceOriginCookie() {
        return ResponseCookie.from("oauth_redirect_origin", "")
                .httpOnly(true)
                .secure(isProduction())
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .path(appProperties.frontend().oauthCallbackPath())
                .build();
    }

    public ResponseCookie createPkceCookie(String name, String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(isProduction())
                .sameSite("Lax")
                .maxAge(Duration.ofMinutes(5))
                .path(appProperties.frontend().oauthCallbackPath())
                .build();
    }

    public ResponseCookie clearPkceCookie(String name) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(isProduction())
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .path(appProperties.frontend().oauthCallbackPath())
                .build();
    }

}
