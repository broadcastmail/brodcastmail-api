package com.broadcastemail.api.onboarding;

import com.broadcastemail.api.TestContainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestContainersConfiguration.class)
class OnboardingControllerTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private OnboardingSessionStore onboardingSessionStore;

    @MockitoBean
    private OnboardingService onboardingService;

    @Test
    void shouldReturnConnectSupabaseWhenNoCookiePresent() {
        // Given — no cookie

        // When
        var response = mockMvc.get()
                .uri("/api/v1/onboarding/status");

        // Then
        assertThat(response)
                .hasStatus(200)
                .bodyJson()
                .extractingPath("$.step")
                .asString()
                .isEqualTo("CONNECT_SUPABASE");
    }

    @Test
    void shouldReturnConfirmSchemaWhenSessionHasNoSchemaDetails() {
        // Given
        String sessionToken = createSession();

        // When
        var response = mockMvc.get()
                .uri("/api/v1/onboarding/status")
                .cookie(new MockCookie("onboarding_session", sessionToken));

        // Then
        assertThat(response)
                .hasStatus(200)
                .bodyJson()
                .extractingPath("$.step")
                .asString()
                .isEqualTo("CONFIRM_SCHEMA");
    }

    @Test
    void shouldReturnConnectResendWhenSchemaConfirmed() {
        // Given
        String sessionToken = createSessionWithSchemaConfirmed();

        // When
        var response = mockMvc.get()
                .uri("/api/v1/onboarding/status")
                .cookie(new MockCookie("onboarding_session", sessionToken));

        // Then
        assertThat(response)
                .hasStatus(200)
                .bodyJson()
                .extractingPath("$.step")
                .asString()
                .isEqualTo("CONNECT_RESEND");
    }

    @Test
    void shouldReturnConfirmAccountWhenSchemaConfirmedAndResendConnected() {
        // Given
        String sessionToken = createSessionWithSchemaConfirmed();
        OnboardingSession session = onboardingSessionStore.get(sessionToken);
        onboardingSessionStore.updateSession(sessionToken,
                session.withResendDetails(
                        new OnboardingSession.ResendDetails(
                                "encrypted-resend-key",
                                "test@example.com")));

        // When
        var response = mockMvc.get()
                .uri("/api/v1/onboarding/status")
                .cookie(new MockCookie("onboarding_session", sessionToken));

        // Then
        assertThat(response)
                .hasStatus(200)
                .bodyJson()
                .extractingPath("$.step")
                .asString()
                .isEqualTo("CONFIRM_ACCOUNT");
    }

    @Test
    void shouldReturnConnectSupabaseWhenSessionExpired() {
        // Given
        String sessionToken = createExpiredSession();

        // When
        var response = mockMvc.get()
                .uri("/api/v1/onboarding/status")
                .cookie(new MockCookie("onboarding_session", sessionToken));

        // Then
        assertThat(response)
                .hasStatus(200)
                .bodyJson()
                .extractingPath("$.step")
                .asString()
                .isEqualTo("CONNECT_SUPABASE");
    }

    @Test
    void shouldReturnConnectSupabaseWhenInvalidSessionToken() {
        // Given
        String fakeToken = "thisisaninvalidsessiontoken";

        // When
        var response = mockMvc.get()
                .uri("/api/v1/onboarding/status")
                .cookie(new MockCookie("onboarding_session", fakeToken));

        // Then
        assertThat(response)
                .hasStatus(200)
                .bodyJson()
                .extractingPath("$.step")
                .asString()
                .isEqualTo("CONNECT_SUPABASE");
    }

    @Test
    void shouldReturn401WhenConfirmingSchemaWithNoSession() {
        // Given — no cookie

        // When
        var response = mockMvc.post()
                .uri("/api/v1/onboarding/schema/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"columnNames\":[\"plan\",\"full_name\"]}")
                .exchange();

        // Then
        assertThat(response).hasStatus(401);
    }

    @Test
    void shouldClearCookieAfterAccountCreation() {
        // Given
        String sessionToken = createSession();
        doNothing().when(onboardingService).completeOnboarding(sessionToken);

        // When
        var response = mockMvc.post()
                .uri("/api/v1/onboarding/complete")
                .cookie(new MockCookie("onboarding_session", sessionToken))
                .exchange();

        // Then
        assertThat(response).hasStatus(302);
        assertThat(response.getResponse().getHeader("Set-Cookie"))
                .contains("onboarding_session=")
                .contains("Max-Age=0");
    }

    @Test
    void shouldInvalidateSessionAfterAccountCreation() {
        // Given
        String sessionToken = createSession();
        doNothing().when(onboardingService).completeOnboarding(sessionToken);

        // When
        mockMvc.post()
                .uri("/api/v1/onboarding/complete")
                .cookie(new MockCookie("onboarding_session", sessionToken))
                .exchange();

        // Then
        verify(onboardingService).completeOnboarding(sessionToken);
    }

    // Helpers
    private OnboardingSession.OnboardingSessionBuilder baseSession() {
        return OnboardingSession.builder()
                .projectRef("project-ref")
                .projectUrl("https://project-ref.supabase.co")
                .jdbcUrl("jdbc:postgresql://db.project-ref.supabase.co:5432/postgres")
                .encryptedRolePassword("encrypted-role-password")
                .ownerEmail("test@example.com")
                .encryptedAccessToken("encrypted-access-token")
                .encryptedRefreshToken("encrypted-refresh-token")
                .tokenExpiresAt(Instant.now().plusSeconds(3600))
                .expiresAt(Instant.now().plus(Duration.ofMinutes(30)));
    }

    private String createSession() {
        return onboardingSessionStore.create(baseSession().build());
    }

    private String createSessionWithSchemaConfirmed() {
        return onboardingSessionStore.create(baseSession()
                .schemaDetails(new OnboardingSession.SchemaDetails("profiles", "public", true))
                .confirmedColumnNames(List.of("plan", "full_name"))
                .build());
    }

    private String createExpiredSession() {
        return onboardingSessionStore.create(baseSession()
                .expiresAt(Instant.now().minusSeconds(1))
                .build());
    }
}