package com.broadcastmail.api;

import com.broadcastmail.api.account.Account;
import com.broadcastmail.api.account.AccountRepository;
import com.broadcastmail.api.common.SecurityUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestContainersConfiguration.class)
class SecurityTest {

    @Autowired
    private MockMvcTester mockMvc;
    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
    }

    private String createTestAccountAndGetApiKey() {
        String rawApiKey = SecurityUtil.generateApiKey();
        Account account = Account.builder()
                .email("test@broadcastmail.io")
                .passwordHash("")
                .apiKeyHash(SecurityUtil.sha256(rawApiKey))
                .plan("free")
                .emailVerified(true)
                .uniqueRecipientsThisPeriod(0)
                .periodResetAt(OffsetDateTime.now(ZoneId.systemDefault()))
                .build();
        accountRepository.save(account);
        return rawApiKey;
    }

    @Test
    void shouldAllowOnboardingEndpointsWithoutApiKey() {
        // Given — no API key in request

        // When
        var response = mockMvc.get()
                .uri("/api/v1/onboarding/status");

        // Then
        assertThat(response).hasStatus(200);
    }


    @Test
    void shouldAllowOAuthEndpointsWithoutApiKey() {
        // Given — no API key in request

        // When
        var response = mockMvc.get()
                .uri("/api/v1/oauth/supabase/authorize");

        // Then
        assertThat(response).hasStatus(302);
    }

    @Test
    void shouldRejectProtectedEndpointsWithoutApiKey() {
        // Given — no API key in request

        // When
        var response = mockMvc.get()
                .uri("/api/v1/anything");

        // Then
        assertThat(response).hasStatus(401);
    }

    @Test
    void shouldRejectProtectedEndpointsWithInvalidApiKey() {
        // Given
        String invalidApiKey = "bm_live_invalidkey123";

        // When
        var response = mockMvc.get()
                .uri("/api/v1/anything")
                .header("X-API-Key", invalidApiKey);

        // Then
        assertThat(response).hasStatus(401);
    }

    @Test
    void shouldAllowProtectedEndpointsWithValidApiKey() {
        // Given
        String apiKey = createTestAccountAndGetApiKey();

        // When
        var response = mockMvc.get()
                .uri("/api/v1/anything")
                .header("X-API-Key", apiKey);

        // Then
        assertThat(response).hasStatus(404);
    }
}
