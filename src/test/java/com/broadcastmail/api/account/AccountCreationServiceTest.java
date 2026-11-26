package com.broadcastmail.api.account;

import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.auth.SessionService;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.config.ResendProperties;
import com.broadcastmail.api.onboarding.dto.AccountCreationResult;
import com.broadcastmail.api.supabase.management.SupabaseManagementClient;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import com.broadcastmail.common.connection.ConnectionRepository;
import com.broadcastmail.common.filterablecolumn.FilterableColumnRepository;
import com.broadcastmail.api.oauth.token.OAuthToken;
import com.broadcastmail.api.onboarding.OnboardingSession;
import com.broadcastmail.api.resend.ResendClient;
import com.broadcastmail.api.oauth.token.OAuthTokenRepository;
import com.broadcastmail.common.emailprovider.EmailProvider;
import com.broadcastmail.common.emailprovider.EmailProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountCreationServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private EmailProviderRepository emailProviderRepository;

    @Mock
    private OAuthTokenRepository oAuthTokenRepository;

    @Mock
    private ConnectionRepository connectionRepository;

    @Mock
    private FilterableColumnRepository filterableColumnRepository;

    @Mock
    private ResendClient resendClient;

    @Mock
    private SupabaseManagementClient supabaseManagementClient;

    @Mock
    private SessionService sessionService;

    @Captor
    private ArgumentCaptor<Account> accountCaptor;

    private static final String ENCRYPTION_KEY = "12345678901234567890123456789012";

    private AccountCreationService accountCreationService;

    @BeforeEach
    void setUp() {
        when(resendClient.registerWebhook(anyString(), anyString()))
                .thenReturn(new ResendClient.RegisteredWebhook("wh_123", "whsec_test"));
        accountCreationService = new AccountCreationService(
                accountRepository,
                emailProviderRepository,
                oAuthTokenRepository,
                filterableColumnRepository,
                connectionRepository,
                resendClient,
                new EncryptionProperties(ENCRYPTION_KEY),
                new ResendProperties("https://webhooks.example.com"),
                supabaseManagementClient,
                sessionService
        );
    }



    private OnboardingSession buildSession() {
        return OnboardingSession.builder()
                .ownerEmail("owner@example.com")
                .encryptedAccessToken("encrypted-access")
                .encryptedRefreshToken("encrypted-refresh")
                .resendDetails(
                        new OnboardingSession.ResendDetails(
                                SecurityUtil.encrypt("re_validkey123", ENCRYPTION_KEY),
                                "hello@example.com"
                        )
                )
                .projectRef("project-ref")
                .projectUrl("https://project-ref.supabase.co")
                .encryptedRolePassword("encrypted-role-password")
                .tokenExpiresAt(Instant.now().plusSeconds(3600))
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();
    }

    @Test
    void shouldCreateAccountWithEmailFromSupabase() {
        // Given
        OnboardingSession session = buildSession();

        // When
        accountCreationService.createFromOnboarding(session, null);

        // Then
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getEmail()).isEqualTo("owner@example.com");
    }

    @Test
    void shouldLogTheOnboardingBrowserInAsTheAccountsFirstDevice() {
        // Given
        OnboardingSession session = buildSession();
        when(sessionService.create(any(), any())).thenReturn("session-token");

        // When
        AccountCreationResult result = accountCreationService.createFromOnboarding(session, null);

        // Then
        assertThat(result.sessionToken()).isEqualTo("session-token");
        verify(sessionService).create(any(), any());
    }

    @Test
    void shouldPersistOAuthTokenAfterAccountCreation() {
        // Given
        OnboardingSession session = buildSession();

        // When
        accountCreationService.createFromOnboarding(session, null);

        // Then
        verify(oAuthTokenRepository).save(any(OAuthToken.class));
    }

    @Test
    void shouldPersistEmailProviderAfterAccountCreation() {
        // Given
        OnboardingSession session = buildSession();

        // When
        accountCreationService.createFromOnboarding(session, null);

        // Then
        verify(emailProviderRepository).save(any(EmailProvider.class));
    }

    @Test
    void shouldPersistConnectionAfterAccountCreation() {
        // Given
        OnboardingSession session = buildSession();

        // When
        accountCreationService.createFromOnboarding(session, null);

        // Then
        InOrder inOrder = inOrder(accountRepository, oAuthTokenRepository, emailProviderRepository);
        inOrder.verify(accountRepository).save(any());
        inOrder.verify(oAuthTokenRepository).save(any());
        inOrder.verify(emailProviderRepository).save(any());
    }


}