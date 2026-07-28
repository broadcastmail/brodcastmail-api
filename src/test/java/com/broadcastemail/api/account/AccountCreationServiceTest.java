package com.broadcastemail.api.account;

import com.broadcastemail.api.connection.ConnectionRepository;
import com.broadcastemail.api.emailprovider.EmailProvider;
import com.broadcastemail.api.emailprovider.EmailProviderRepository;
import com.broadcastemail.api.filterablecolumn.FilterableColumnRepository;
import com.broadcastemail.api.oauth.OAuthToken;
import com.broadcastemail.api.onboarding.OnboardingSession;
import com.broadcastemail.api.token.OAuthTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

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

    @Mock
    private ConnectionRepository connectionRepository;

    @Mock
    private FilterableColumnRepository filterableColumnRepository;

    @Captor
    private ArgumentCaptor<Account> accountCaptor;

    @InjectMocks
    private AccountCreationService accountCreationService;



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
    void shouldReturnUniqueApiKeyPerAccount() {
        // Given
        OnboardingSession session = buildSession();

        // When
        Account account1 = accountCreationService.createFromOnboarding(session);
        Account account2 = accountCreationService.createFromOnboarding(session);

        // Then
        assertThat(account1.getApiKeyHash()).isNotBlank();
        assertThat(account1.getApiKeyHash()).isNotEqualTo(account2.getApiKeyHash());
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