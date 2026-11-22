package com.broadcastmail.api.onboarding;

import com.broadcastmail.api.IntegrationTest;
import com.broadcastmail.api.TestContainersConfiguration;
import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.common.exceptions.EmailAlreadyTakenException;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.connection.ConnectionService;
import com.broadcastmail.api.oauth.OAuthSessionStore;
import com.broadcastmail.api.oauth.token.OAuthTokenRepository;
import com.broadcastmail.api.resend.ResendClient;
import com.broadcastmail.api.support.CampaignTestFixtures;
import com.broadcastmail.common.account.AccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestContainersConfiguration.class)
class OnboardingServiceIntegrationTest extends IntegrationTest {

    private static final String OWNER_EMAIL = "owner@example.com";

    @Autowired
    private OnboardingService onboardingService;

    @Autowired
    private OAuthSessionStore onboardingSessionStore;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OAuthTokenRepository oAuthTokenRepository;

    @Autowired
    private EncryptionProperties encryptionProperties;

    @MockitoBean
    private SessionReaderService sessionReaderService;

    @MockitoBean
    private ResendClient resendClient;

    @MockitoBean
    private ConnectionService connectionService;

    @AfterEach
    void tearDown() {
        oAuthTokenRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void shouldRollBackAccountAndRemoveWebhookWhenConnectionCannotBeSaved() {
        // Given
        String token = readySession();
        when(resendClient.registerWebhook(anyString(), anyString()))
                .thenReturn(new ResendClient.RegisteredWebhook("wh_1", "whsec_test"));
        doThrow(new IllegalStateException("connection insert failed"))
                .when(connectionService).createConnection(any(), any(), any(), any());

        // When / Then
        assertThatThrownBy(() -> onboardingService.completeOnboarding(token))
                .hasMessage("connection insert failed");
        assertThat(accountRepository.count()).isZero();
        assertThat(oAuthTokenRepository.count()).isZero();
        verify(resendClient).deleteWebhookQuietly("re_test_key", "wh_1");
    }

    @Test
    void shouldRejectExistingEmailBeforeCreatingAnything() {
        // Given
        accountRepository.saveAndFlush(CampaignTestFixtures.account().email(OWNER_EMAIL).build());
        String token = readySession();

        // When / Then
        assertThatThrownBy(() -> onboardingService.completeOnboarding(token))
                .isInstanceOf(EmailAlreadyTakenException.class);
        assertThat(accountRepository.count()).isEqualTo(1);
        verify(resendClient, never()).registerWebhook(anyString(), anyString());
    }

    private String readySession() {
        OnboardingSession session = OnboardingSession.builder()
                .projectRef("ref")
                .projectUrl("https://ref.supabase.co")
                .poolerPrefix("aws-0-eu-west-1")
                .dbRole("broadcastmail_r_abc")
                .encryptedRolePassword("enc-pw")
                .connectionVerified(true)
                .ownerEmail(OWNER_EMAIL)
                .encryptedAccessToken("enc-access")
                .encryptedRefreshToken("enc-refresh")
                .tokenExpiresAt(Instant.now().plusSeconds(3600))
                .expiresAt(Instant.now().plusSeconds(1800))
                .schemaDetails(new OnboardingSession.SchemaDetails("profiles", "public", "id", true))
                .emailTableDetails(new OnboardingSession.EmailTableDetails("public", "profiles", "id", "email"))
                .emailTableConfirmed(true)
                .confirmedColumnNames(List.of())
                .resendDetails(new OnboardingSession.ResendDetails(
                        SecurityUtil.encrypt("re_test_key", encryptionProperties.key()), "hello@example.com"))
                .build();
        String token = onboardingSessionStore.create(session);
        when(sessionReaderService.ensureReady(token)).thenReturn(session);
        return token;
    }
}
