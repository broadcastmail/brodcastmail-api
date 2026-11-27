package com.broadcastmail.api.config;

import com.broadcastmail.api.IntegrationTest;
import com.broadcastmail.api.TestContainersConfiguration;
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

import static com.broadcastmail.api.support.CampaignTestFixtures.TEST_SESSION_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

/** Runs the real authenticated filter chain (no TestSecurityConfig) to cover cross-origin CSRF for cookie-authenticated calls. */
@SpringBootTest(properties = "app.frontend.cookie-domain=.broadcastmail.test")
@AutoConfigureMockMvc
@Import(TestContainersConfiguration.class)
class CsrfIntegrationTest extends IntegrationTest {

    private static final String ORIGIN = "http://localhost:3000";
    private static final String PROBE = "/api/v1/csrf-probe";

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private SessionRepository sessionRepository;

    @BeforeEach
    void setUp() {
        Account account = accountRepository.saveAndFlush(CampaignTestFixtures.account().build());
        sessionRepository.save(CampaignTestFixtures.session(account.getId()).build());
    }

    @AfterEach
    void tearDown() {
        accountRepository.deleteAll();
    }

    @Test
    void shouldIssueXsrfCookieOnSharedDomainWhenRequestIsAuthenticated() {
        // When
        var response = mockMvc.get().uri("/api/v1/me").header("Origin", ORIGIN).cookie(session()).exchange();

        // Then
        Cookie xsrf = response.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        assertThat(xsrf.getDomain()).isEqualTo(".broadcastmail.test");
        assertThat(xsrf.isHttpOnly()).isFalse();
    }

    @Test
    void shouldReturn403WhenCookieAuthenticatedPostHasNoCsrfHeader() {
        // When
        var response = mockMvc.post().uri(PROBE).header("Origin", ORIGIN).cookie(session()).exchange();

        // Then
        assertThat(response).hasStatus(403);
    }

    @Test
    void shouldPassCsrfWhenHeaderEchoesXsrfCookie() {
        // Given
        String token = mockMvc.get().uri("/api/v1/me").header("Origin", ORIGIN).cookie(session()).exchange()
                .getResponse().getCookie("XSRF-TOKEN").getValue();

        // When
        var response = mockMvc.post().uri(PROBE)
                .header("Origin", ORIGIN)
                .header("X-XSRF-TOKEN", token)
                .cookie(session(), new MockCookie("XSRF-TOKEN", token))
                .exchange();

        // Then — past CSRF; the probe path itself doesn't exist
        assertThat(response.getResponse().getStatus()).isNotEqualTo(403);
    }

    private static MockCookie session() {
        return new MockCookie("bm_session", TEST_SESSION_TOKEN);
    }
}
