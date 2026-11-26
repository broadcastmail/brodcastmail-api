package com.broadcastmail.api.auth;

import com.broadcastmail.api.IntegrationTest;
import com.broadcastmail.api.TestContainersConfiguration;
import com.broadcastmail.api.TestSecurityConfig;
import com.broadcastmail.api.support.CampaignTestFixtures;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.springframework.mock.web.MockCookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestContainersConfiguration.class, TestSecurityConfig.class})
@ActiveProfiles("test")
class AuthControllerTest extends IntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private SessionService sessionService;

    @AfterEach
    void tearDown() {
        accountRepository.deleteAll();
    }

    @Test
    void shouldLogOutOnlyTheDeviceThatLoggedOut() {
        // Given: the same account logged in on a laptop and a phone
        Account account = accountRepository.save(CampaignTestFixtures.account().build());
        String laptop = sessionService.create(account.getId(), null);
        String phone = sessionService.create(account.getId(), null);

        // When
        var response = mockMvc.post().uri("/api/v1/auth/logout").cookie(new MockCookie("bm_session", laptop)).exchange();

        // Then: the laptop's session is gone, even for a copy of its cookie; the phone stays logged in
        assertThat(response).hasStatus(204);
        assertThat(sessionService.validate(laptop)).isEmpty();
        assertThat(sessionService.validate(phone)).isPresent();
    }

    @Test
    void shouldReturn204OnLogout() {
        // When
        var response = mockMvc.post()
                .uri("/api/v1/auth/logout")
                .exchange();

        // Then
        assertThat(response).hasStatus(204);
    }

    @Test
    void shouldClearSessionCookieOnLogout() {
        // When
        var response = mockMvc.post()
                .uri("/api/v1/auth/logout")
                .exchange();

        // Then
        assertThat(response.getResponse().getHeader("Set-Cookie"))
                .contains("bm_session=")
                .contains("Max-Age=0");
    }
}
