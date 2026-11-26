package com.broadcastmail.api.auth;

import com.broadcastmail.api.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CookieServiceTest {

    private CookieService cookieService;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties(
                "test-secret",
                new AppProperties.Frontend(List.of("http://localhost:3000"), "/api/v1/oauth/supabase/callback", "http://localhost:3000", null)
        );
        cookieService = new CookieService(appProperties);
    }

    private void activeProfile(String profile) {
        ReflectionTestUtils.setField(cookieService, "activeProfile", profile);
    }

    @Test
    void sessionCookieShouldBeLaxAndInsecureOutsideProd() {
        // Given
        activeProfile("dev");

        // When
        ResponseCookie cookie = cookieService.createSessionCookie("session-token", Duration.ofDays(30));

        // Then
        assertThat(cookie.getName()).isEqualTo("bm_session");
        assertThat(cookie.getValue()).isEqualTo("session-token");
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
        ResponseCookie cookie = cookieService.createSessionCookie("session-token", Duration.ofDays(30));

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

    @Test
    void sessionAndOnboardingCookiesShouldShareParentDomainWhenConfigured() {
        // Given
        CookieService shared = new CookieService(new AppProperties("test-secret", new AppProperties.Frontend(
                List.of("https://dev.broadcastmail.dev"), "/api/v1/oauth/supabase/callback",
                "https://dev.broadcastmail.dev", ".broadcastmail.dev")));
        ReflectionTestUtils.setField(shared, "activeProfile", "prod");

        // When
        List<ResponseCookie> cookies = List.of(
                shared.createSessionCookie("session-token", Duration.ofDays(30)),
                shared.clearSessionCookie(),
                shared.createOnboardingCookie("token"),
                shared.clearOnboardingCookie());

        // Then
        assertThat(cookies).extracting(ResponseCookie::getDomain).containsOnly(".broadcastmail.dev");
    }

    @Test
    void cookiesShouldStayHostOnlyWhenDomainIsUnset() {
        // Given
        activeProfile("dev");

        // When
        ResponseCookie cookie = cookieService.createOnboardingCookie("token");

        // Then
        assertThat(cookie.getDomain()).isNull();
        assertThat(cookie.toString()).doesNotContain("Domain=");
    }

    @Test
    void pkceCookiesShouldStayHostOnlyEvenWhenDomainIsConfigured() {
        // Given
        CookieService shared = new CookieService(new AppProperties("test-secret", new AppProperties.Frontend(
                List.of("https://dev.broadcastmail.dev"), "/api/v1/oauth/supabase/callback",
                "https://dev.broadcastmail.dev", ".broadcastmail.dev")));
        ReflectionTestUtils.setField(shared, "activeProfile", "prod");

        // When
        ResponseCookie cookie = shared.createPkceCookie("oauth_code_verifier", "verifier");

        // Then
        assertThat(cookie.getDomain()).isNull();
    }
}
