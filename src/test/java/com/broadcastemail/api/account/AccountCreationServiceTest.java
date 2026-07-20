package com.broadcastemail.api.account;

import com.broadcastemail.api.emailprovider.EmailProvider;
import com.broadcastemail.api.emailprovider.EmailProviderRepository;
import com.broadcastemail.api.oauth.OAuthToken;
import com.broadcastemail.api.onboarding.OnboardingSession;
import com.broadcastemail.api.token.OAuthTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountCreationServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private EmailProviderRepository emailProviderRepository;

    @Mock
    private OAuthTokenRepository oAuthTokenRepository;

    @Captor
    private ArgumentCaptor<Account> accountCaptor;

    private AccountCreationService accountCreationService;

    @BeforeEach
    void setUp() {
        accountCreationService = new AccountCreationService(
                accountRepository,
                emailProviderRepository,
                oAuthTokenRepository
        );
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });
    }

    private OnboardingSession buildSession() {
        return OnboardingSession.builder()
                .ownerEmail("owner@example.com")
                .encryptedAccessToken("encrypted-access")
                .encryptedRefreshToken("encrypted-refresh")
                .resendDetails(
                        new OnboardingSession.ResendDetails(
                                "encrypted-resend-key",
                                "hello@example.com"
                        )
                )
                .projectRef("project-ref")
                .projectUrl("https://project-ref.supabase.co")
                .jdbcUrl("jdbc:postgresql://db.project-ref.supabase.co:5432/postgres")
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
        accountCreationService.createFromOnboarding(session);

        // Then
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getEmail()).isEqualTo("owner@example.com");
    }

    @Test
    void shouldReturnApiKeyOnceOnCompletion() {
        // Given
        OnboardingSession session = buildSession();

        // When
        String apiKey1 = accountCreationService.createFromOnboarding(session);
        String apiKey2 = accountCreationService.createFromOnboarding(session);

        // Then
        assertThat(apiKey1).isNotBlank();
        assertThat(apiKey1).isNotEqualTo(apiKey2);
    }

    @Test
    void shouldPersistOAuthTokenAfterAccountCreation() {
        // Given
        OnboardingSession session = buildSession();

        // When
        accountCreationService.createFromOnboarding(session);

        // Then
        verify(oAuthTokenRepository).save(any(OAuthToken.class));
    }

    @Test
    void shouldPersistEmailProviderAfterAccountCreation() {
        // Given
        OnboardingSession session = buildSession();

        // When
        accountCreationService.createFromOnboarding(session);

        // Then
        verify(emailProviderRepository).save(any(EmailProvider.class));
    }

    @Test
    void shouldPersistConnectionAfterAccountCreation() {
        // Given
        OnboardingSession session = buildSession();

        // When
        accountCreationService.createFromOnboarding(session);

        // Then
        InOrder inOrder = inOrder(accountRepository, oAuthTokenRepository, emailProviderRepository);
        inOrder.verify(accountRepository).save(any());
        inOrder.verify(oAuthTokenRepository).save(any());
        inOrder.verify(emailProviderRepository).save(any());
    }


}