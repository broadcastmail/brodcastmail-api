package com.broadcastmail.api.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class CookieService {
    @Value("${spring.profiles.active:dev}")
    private String activeProfile;
    private static final String COOKIE_NAME = "bm_session";

    private boolean isProduction() {
        return "prod".equals(activeProfile);
    }

    public ResponseCookie createSessionCookie(String rawApiKey) {
        return ResponseCookie.from(COOKIE_NAME, rawApiKey)
                .httpOnly(true)
                .secure(isProduction())
                .sameSite(isProduction() ? "None" : "Lax")
                .path("/")
                .maxAge(Duration.ofDays(30))
                .build();
    }

    public ResponseCookie clearSessionCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(isProduction())
                .sameSite(isProduction() ? "None" : "Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }

    public ResponseCookie createOnboardingCookie(String sessionToken) {
        return ResponseCookie.from("onboarding_session", sessionToken)
                .httpOnly(true)
                .secure(isProduction())
                .sameSite("Lax")
                .maxAge(Duration.ofMinutes(30))
                .path("/")
                .build();
    }

    public ResponseCookie clearOnboardingCookie() {
        return ResponseCookie.from("onboarding_session", "")
                .httpOnly(true)
                .secure(isProduction())
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .path("/")
                .build();
    }
}
