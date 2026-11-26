package com.broadcastmail.api.account;

import com.broadcastmail.api.auth.SessionService;
import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.config.ResendProperties;
import com.broadcastmail.api.onboarding.dto.AccountCreationResult;
import com.broadcastmail.api.supabase.management.SupabaseManagementClient;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import com.broadcastmail.common.account.plan.Plan;
import com.broadcastmail.common.connection.ConnectionRepository;
import com.broadcastmail.common.filterablecolumn.FilterableColumnRepository;
import com.broadcastmail.api.oauth.token.OAuthToken;
import com.broadcastmail.api.resend.ResendClient;
import com.broadcastmail.api.oauth.token.OAuthTokenRepository;
import com.broadcastmail.api.onboarding.OnboardingSession;
import com.broadcastmail.common.emailprovider.EmailProvider;
import com.broadcastmail.common.emailprovider.EmailProviderRepository;
import com.broadcastmail.api.common.exceptions.EmailAlreadyTakenException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class AccountCreationService {
    private static final Logger log = LoggerFactory.getLogger(AccountCreationService.class);

    private final AccountRepository accountRepository;
    private final EmailProviderRepository emailProviderRepository;
    private final OAuthTokenRepository oAuthTokenRepository;
    private final FilterableColumnRepository filterableColumnRepository;
    private final ConnectionRepository connectionRepository;
    private final ResendClient resendClient;
    private final EncryptionProperties encryptionProperties;
    private final ResendProperties resendProperties;
    private final SupabaseManagementClient supabaseManagementClient;
    private final SessionService sessionService;

    @Transactional
    public AccountCreationResult createFromOnboarding(OnboardingSession session, @Nullable String userAgent) {
        String ownerEmail = session.getOwnerEmail();
        if (ownerEmail == null) {
            String rawAccessToken = SecurityUtil.decrypt(session.getEncryptedAccessToken(), encryptionProperties.key());
            try {
                ownerEmail = supabaseManagementClient.resolveOwnerEmail(rawAccessToken, session.getProjectRef());
            } catch (Exception e) {
                log.warn("createFromOnboarding: could not resolve owner email for project {}: {}", session.getProjectRef(), e.getMessage());
            }
        }

        if (ownerEmail != null && accountRepository.findByEmail(ownerEmail).isPresent()) {
            throw new EmailAlreadyTakenException("An account with this email already exists. Sign in instead.");
        }

        Account account = Account.builder()
                .email(ownerEmail)
                .passwordHash("")
                .plan(Plan.FREE)
                .emailVerified(true)
                .build();
        accountRepository.save(account);

        OAuthToken token = OAuthToken.builder()
                .accountId(account.getId())
                .accessToken(session.getEncryptedAccessToken())
                .refreshToken(session.getEncryptedRefreshToken())
                .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1))
                .build();
        oAuthTokenRepository.save(token);

        String rawResendApiKey = SecurityUtil.decrypt(session.getResendDetails().encryptedResendApiKey(), encryptionProperties.key());
        String webhookEndpoint = StringUtils.trimTrailingCharacter(resendProperties.webhookBaseUrl(), '/')
                + "/webhooks/resend/" + account.getId();
        ResendClient.RegisteredWebhook webhook = resendClient.registerWebhook(rawResendApiKey, webhookEndpoint);
        removeWebhookIfRolledBack(rawResendApiKey, webhook.id());

        EmailProvider emailProvider = EmailProvider.builder()
                .accountId(account.getId())
                .type("resend")
                .encryptedApiKey(session.getResendDetails().encryptedResendApiKey())
                .fromAddress(session.getResendDetails().fromAddress())
                .encryptedWebhookSecret(SecurityUtil.encrypt(webhook.signingSecret(), encryptionProperties.key()))
                .resendWebhookId(webhook.id())
                .build();
        emailProviderRepository.save(emailProvider);

        return new AccountCreationResult(sessionService.create(account.getId(), userAgent), account.getId());
    }

    // Resend isn't part of the transaction: if account creation rolls back, the webhook would point at nothing.
    private void removeWebhookIfRolledBack(String apiKey, String webhookId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) resendClient.deleteWebhookQuietly(apiKey, webhookId);
            }
        });
    }
}
