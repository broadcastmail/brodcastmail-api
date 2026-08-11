package com.broadcastmail.api.account;

import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.config.ResendProperties;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import com.broadcastmail.common.connection.ConnectionRepository;
import com.broadcastmail.api.filterablecolumn.FilterableColumnRepository;
import com.broadcastmail.api.oauth.OAuthToken;
import com.broadcastmail.api.resend.ResendClient;
import com.broadcastmail.api.token.OAuthTokenRepository;
import com.broadcastmail.api.onboarding.OnboardingSession;
import com.broadcastmail.common.emailprovider.EmailProvider;
import com.broadcastmail.common.emailprovider.EmailProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class AccountCreationService {

    private final AccountRepository accountRepository;
    private final EmailProviderRepository emailProviderRepository;
    private final OAuthTokenRepository oAuthTokenRepository;
    private final FilterableColumnRepository filterableColumnRepository;
    private final ConnectionRepository connectionRepository;
    private final ResendClient resendClient;
    private final EncryptionProperties encryptionProperties;
    private final ResendProperties resendProperties;

    @Transactional
    public Account createFromOnboarding(OnboardingSession session) {
        String rawApiKey = SecurityUtil.generateApiKey();
        String hashedApiKey = SecurityUtil.sha256(rawApiKey);
        Account account = Account.builder()
                .email(session.getOwnerEmail())
                .passwordHash("")
                .apiKeyHash(hashedApiKey)
                .plan("free")
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
        String webhookEndpoint = resendProperties.webhookBaseUrl() + "/webhooks/resend/" + account.getId();
        ResendClient.RegisteredWebhook webhook = resendClient.registerWebhook(rawResendApiKey, webhookEndpoint);

        EmailProvider emailProvider = EmailProvider.builder()
                .accountId(account.getId())
                .type("resend")
                .encryptedApiKey(session.getResendDetails().encryptedResendApiKey())
                .fromAddress(session.getResendDetails().fromAddress())
                .encryptedWebhookSecret(SecurityUtil.encrypt(webhook.signingSecret(), encryptionProperties.key()))
                .resendWebhookId(webhook.id())
                .build();
        emailProviderRepository.save(emailProvider);
        return account;
    }
}
