package com.broadcastemail.api.account;

import com.broadcastemail.api.common.SecurityUtil;
import com.broadcastemail.api.emailprovider.EmailProvider;
import com.broadcastemail.api.emailprovider.EmailProviderRepository;
import com.broadcastemail.api.oauth.OAuthToken;
import com.broadcastemail.api.token.OAuthTokenRepository;
import com.broadcastemail.api.onboarding.OnboardingSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class AccountCreationService {

    private final AccountRepository accountRepository;
    private final EmailProviderRepository emailProviderRepository;
    private final OAuthTokenRepository oAuthTokenRepository;

    @Transactional
    public String createFromOnboarding(OnboardingSession session)
    {
        String rawApiKey = SecurityUtil.generateApiKey();
        String hashedApiKey = SecurityUtil.sha256(rawApiKey);
        // create account
        Account account = Account.builder()
                .email(session.getOwnerEmail())
                .passwordHash("") // no password yet — OAuth only
                .apiKeyHash(hashedApiKey)
                .plan("free")
                .emailVerified(true) // Supabase OAuth verified them
                .uniqueRecipientsThisPeriod(0)
                .periodResetAt(OffsetDateTime.now(ZoneId.systemDefault()))
                .build();
        accountRepository.save(account);
        OAuthToken token = OAuthToken.builder()
                .accountId(account.getId())
                .accessToken(session.getEncryptedAccessToken())
                .refreshToken(session.getEncryptedRefreshToken())
                .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1))
                .build();
        oAuthTokenRepository.save(token);

        EmailProvider emailProvider = EmailProvider.builder()
                .accountId(account.getId())
                .type("resend")
                .encryptedApiKey(session.getResendDetails().encryptedResendApiKey())
                .fromAddress(session.getResendDetails().fromAddress())
                .build();

        emailProviderRepository.save(emailProvider);

        return rawApiKey;
    }
}
