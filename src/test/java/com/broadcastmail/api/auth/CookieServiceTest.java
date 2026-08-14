package com.broadcastmail.api.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CookieServiceTest {

    private CookieService cookieService;

    @BeforeEach
    void setUp() {
        cookieService = new CookieService();
    }

    private void activeProfile(String profile) {
        ReflectionTestUtils.setField(cookieService, "activeProfile", profile);
    }

    @Test
    void sessionCookieShouldBeLaxAndInsecureOutsideProd() {
        // Given
        activeProfile("dev");

        // When
        ResponseCookie cookie = cookieService.createSessionCookie("bm_live_testkey");

        // Then
        assertThat(cookie.getName()).isEqualTo("bm_session");
        assertThat(cookie.getValue()).isEqualTo("bm_live_testkey");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void sessionCookieShouldBeNoneAndSecureInProd() {
        // Given
        activeProfile("prod");

        // When
        ResponseCookie cookie = cookieService.createSessionCookie("bm_live_testkey");

        // Then
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("None");
    }

    @Test
    void clearSessionCookieShouldExpireImmediately() {
        // Given
        activeProfile("dev");

        // When
        ResponseCookie cookie = cookieService.clearSessionCookie();

        // Then
        assertThat(cookie.getName()).isEqualTo("bm_session");
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
    }

    @Test
    void onboardingCookieShouldAlwaysBeLaxRegardlessOfProfile() {
        // Given
        activeProfile("prod");

        // When
        ResponseCookie cookie = cookieService.createOnboardingCookie("session-token");

        // Then
        assertThat(cookie.getName()).isEqualTo("onboarding_session");
        assertThat(cookie.getValue()).isEqualTo("session-token");
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void clearOnboardingCookieShouldExpireImmediately() {
        // Given
        activeProfile("dev");

        // When
        ResponseCookie cookie = cookieService.clearOnboardingCookie();

        // Then
        assertThat(cookie.getName()).isEqualTo("onboarding_session");
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
    }
}
