package com.broadcastmail.api.emailprovider;

import com.broadcastmail.api.common.exceptions.InvalidOnboardingSessionException;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.onboarding.OnboardingSession;
import com.broadcastmail.api.onboarding.OnboardingSessionStore;
import com.broadcastmail.api.resend.ResendClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailProviderServiceTest {

    @Mock
    private OnboardingSessionStore onboardingSessionStore;

    @Mock
    private ResendClient resendClient;

    @Captor
    private ArgumentCaptor<OnboardingSession> sessionCaptor;

    private EmailProviderService emailProviderService;

    @BeforeEach
    void setUp() {
        emailProviderService = new EmailProviderService(
                onboardingSessionStore,
                resendClient,
                new EncryptionProperties("12345678901234567890123456789012")

        );
    }

    private OnboardingSession sessionWithSchemaConfirmed() {
        return OnboardingSession.builder()
                .ownerEmail("test@example.com")
                .encryptedAccessToken("encrypted-access")
                .encryptedRefreshToken("encrypted-refresh")
                .projectRef("project-ref")
                .projectUrl("https://project-ref.supabase.co")
                .jdbcUrl("jdbc:postgresql://db.project-ref.supabase.co:5432/postgres")
                .encryptedRolePassword("encrypted-role-password")
                .tokenExpiresAt(Instant.now().plusSeconds(3600))
                .expiresAt(Instant.now().plusSeconds(1800))
                .schemaDetails(new OnboardingSession.SchemaDetails("profiles", "public", true))
                .confirmedColumnNames(List.of("plan", "full_name"))
                .build();
    }

    @Test
    void shouldThrowWhenSessionTokenIsNull() {
        // Given / When / Then
        assertThatThrownBy(() -> emailProviderService.addEmailProvider(null, "re_validkey123", "hello@example.com"))
                .isInstanceOf(InvalidOnboardingSessionException.class);
    }

    @Test
    void shouldThrowWhenSchemaNotConfirmed() {
        // Given
        OnboardingSession session = OnboardingSession.builder()
                .ownerEmail("test@example.com")
                .encryptedAccessToken("encrypted-access")
                .encryptedRefreshToken("encrypted-refresh")
                .projectRef("project-ref")
                .projectUrl("https://project-ref.supabase.co")
                .jdbcUrl("jdbc:postgresql://db.project-ref.supabase.co:5432/postgres")
                .encryptedRolePassword("encrypted-role-password")
                .tokenExpiresAt(Instant.now().plusSeconds(3600))
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();
        when(onboardingSessionStore.get("token")).thenReturn(session);

        // When / Then
        assertThatThrownBy(() -> emailProviderService.addEmailProvider("token", "re_validkey123", "hello@example.com"))
                .isInstanceOf(InvalidOnboardingSessionException.class);
    }

    @Test
    void shouldUpdateSessionWithResendDetailsAfterValidation() {
        // Given
        when(onboardingSessionStore.get("token")).thenReturn(sessionWithSchemaConfirmed());
        doNothing().when(resendClient).validateApiKey(anyString());

        // When
        emailProviderService.addEmailProvider("token", "re_validkey123", "hello@example.com");

        // Then
        verify(onboardingSessionStore).updateSession(eq("token"), sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getResendDetails()).isNotNull();
        assertThat(sessionCaptor.getValue().getResendDetails().fromAddress()).isEqualTo("hello@example.com");
    }


}