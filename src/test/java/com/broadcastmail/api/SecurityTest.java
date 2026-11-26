package com.broadcastmail.api;

import com.broadcastmail.api.auth.SessionService;
import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.support.CampaignTestFixtures;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import com.broadcastmail.common.session.SessionRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.OffsetDateTime;

import static com.broadcastmail.api.support.CampaignTestFixtures.TEST_SESSION_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestContainersConfiguration.class)
class SecurityTest extends IntegrationTest {

    // Not mapped: past the security chain it answers 404, which proves the request was let through.
    private static final String PROTECTED = "/api/v1/anything";

    @Autowired
    private MockMvcTester mockMvc;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private SessionService sessionService;

    private Account account;

    @BeforeEach
    void setUp() {
        account = accountRepository.save(CampaignTestFixtures.account().build());
    }

    @AfterEach
    void tearDown() {
        sessionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void shouldAllowOnboardingEndpointsWithoutSession() {
        // When
        var response = mockMvc.get().uri("/api/v1/onboarding/status");

        // Then
        assertThat(response).hasStatus(200);
    }

    @Test
    void shouldAllowOAuthEndpointsWithoutSession() {
        // When
        var response = mockMvc.get().uri("/api/v1/oauth/supabase/projects").param("partialToken", "any");

        // Then: 401 (invalid token) not 403 (blocked by security filter), so the endpoint is reachable
        assertThat(response).hasStatus(401);
    }

    @Test
    void shouldRejectProtectedEndpointsWithoutSession() {
        // When
        var response = mockMvc.get().uri(PROTECTED);

        // Then
        assertThat(response).hasStatus(401);
    }

    @Test
    void shouldRejectAnUnknownSessionToken() {
        // When
        var response = mockMvc.get().uri(PROTECTED).cookie(new MockCookie("bm_session", "unknown-token"));

        // Then
        assertThat(response).hasStatus(401);
    }

    @Test
    void shouldRejectAnExpiredSession() {
        // Given
        sessionRepository.save(CampaignTestFixtures.session(account.getId())
                .expiresAt(OffsetDateTime.now().minusMinutes(1)).build());

        // When
        var response = mockMvc.get().uri(PROTECTED).cookie(session(TEST_SESSION_TOKEN));

        // Then
        assertThat(response).hasStatus(401);
    }

    @Test
    void shouldAllowProtectedEndpointsWithAValidSession() {
        // Given
        sessionRepository.save(CampaignTestFixtures.session(account.getId()).build());

        // When
        var response = mockMvc.get().uri(PROTECTED).cookie(session(TEST_SESSION_TOKEN));

        // Then
        assertThat(response).hasStatus(404);
    }

    @Test
    void shouldIgnoreAnApiKeyHeaderEvenWithAValidToken() {
        // Given: the header path is gone; only the cookie authenticates
        sessionRepository.save(CampaignTestFixtures.session(account.getId()).build());

        // When
        var response = mockMvc.get().uri(PROTECTED).header("X-API-Key", TEST_SESSION_TOKEN);

        // Then
        assertThat(response).hasStatus(401);
    }

    @Test
    void shouldKeepTheFirstDeviceLoggedInWhenASecondOneLogsIn() {
        // Given
        String laptop = sessionService.create(account.getId(), null);
        String phone = sessionService.create(account.getId(), null);

        // When
        var laptopResponse = mockMvc.get().uri(PROTECTED).cookie(session(laptop));
        var phoneResponse = mockMvc.get().uri(PROTECTED).cookie(session(phone));

        // Then
        assertThat(laptopResponse).hasStatus(404);
        assertThat(phoneResponse).hasStatus(404);
    }

    @Test
    void shouldSlideTheSessionAndRefreshTheCookieWhenTheDeviceWasLastSeenOverAnHourAgo() {
        // Given: logged in 10 days ago, last seen 2 hours ago
        OffsetDateTime now = OffsetDateTime.now();
        sessionRepository.save(CampaignTestFixtures.session(account.getId())
                .createdAt(now.minusDays(10)).lastSeenAt(now.minusHours(2)).expiresAt(now.plusDays(20)).build());

        // When
        var response = mockMvc.get().uri(PROTECTED).cookie(session(TEST_SESSION_TOKEN)).exchange();

        // Then: 30 days from now again, and the browser is told so
        Cookie refreshed = response.getResponse().getCookie("bm_session");
        assertThat(refreshed).isNotNull();
        assertThat(refreshed.getValue()).isEqualTo(TEST_SESSION_TOKEN);
        assertThat(refreshed.getMaxAge()).isBetween(29 * 24 * 3600, 30 * 24 * 3600);
        assertThat(sessionRepository.findByTokenHashAndExpiresAtAfter(SecurityUtil.sha256(TEST_SESSION_TOKEN), now))
                .get().extracting(s -> s.getExpiresAt().isAfter(now.plusDays(29))).isEqualTo(true);
    }

    @Test
    void shouldNotSlideBeyond90DaysAfterLogin() {
        // Given: logged in 89 days ago, still active
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime loggedIn = now.minusDays(89);
        sessionRepository.save(CampaignTestFixtures.session(account.getId())
                .createdAt(loggedIn).lastSeenAt(now.minusHours(2)).expiresAt(now.plusDays(1)).build());

        // When
        var response = mockMvc.get().uri(PROTECTED).cookie(session(TEST_SESSION_TOKEN)).exchange();

        // Then: the cookie lives only until the 90-day cap, about a day
        assertThat(response.getResponse().getCookie("bm_session").getMaxAge()).isBetween(23 * 3600, 24 * 3600);
        assertThat(sessionRepository.findByTokenHashAndExpiresAtAfter(SecurityUtil.sha256(TEST_SESSION_TOKEN), now))
                .get().extracting(s -> s.getExpiresAt().isAfter(loggedIn.plusDays(90).plusMinutes(1))).isEqualTo(false);
    }

    @Test
    void shouldNotRefreshTheCookieWhenTheDeviceWasSeenWithinTheHour() {
        // Given
        sessionRepository.save(CampaignTestFixtures.session(account.getId()).build());

        // When
        var response = mockMvc.get().uri(PROTECTED).cookie(session(TEST_SESSION_TOKEN)).exchange();

        // Then
        assertThat(response.getResponse().getCookie("bm_session")).isNull();
    }

    @Test
    void shouldRejectAMutationWithoutCsrfToken() {
        // Given
        sessionRepository.save(CampaignTestFixtures.session(account.getId()).build());

        // When
        var response = mockMvc.post().uri(PROTECTED).cookie(session(TEST_SESSION_TOKEN)).exchange();

        // Then
        assertThat(response).hasStatus(403);
    }

    private static MockCookie session(String token) {
        return new MockCookie("bm_session", token);
    }
}
