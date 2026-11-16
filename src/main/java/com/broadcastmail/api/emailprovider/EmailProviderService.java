package com.broadcastmail.api.emailprovider;

import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.common.exceptions.EmailProviderNotFoundException;
import com.broadcastmail.api.common.exceptions.InvalidOnboardingSessionException;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.emailprovider.dto.EmailProviderInfoResponse;
import com.broadcastmail.api.emailprovider.dto.VerifyKeyResponse;
import com.broadcastmail.api.onboarding.OnboardingSession;
import com.broadcastmail.api.oauth.OAuthSessionStore;
import com.broadcastmail.api.resend.ResendClient;
import com.broadcastmail.common.emailprovider.EmailProvider;
import com.broadcastmail.common.emailprovider.EmailProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailProviderService {

    private final OAuthSessionStore onboardingSessionStore;
    private final ResendClient resendClient;
    private final EncryptionProperties encryptionProperties;
    private final EmailProviderRepository emailProviderRepository;


    public VerifyKeyResponse verifyKey(String apiKey) {
        resendClient.validateApiKey(apiKey);
        return new VerifyKeyResponse(resendClient.listVerifiedDomains(apiKey));
    }

    public VerifyKeyResponse verifyKeyAndStore(String sessionToken, String apiKey) {
        onboardingSessionStore.get(sessionToken).requireSchemaConfirmed();
        List<String> domains = resendClient.listVerifiedDomains(apiKey);
        String encrypted = SecurityUtil.encrypt(apiKey, encryptionProperties.key());
        OnboardingSession existing = onboardingSessionStore.get(sessionToken);
        onboardingSessionStore.updateSession(sessionToken,
                existing.withResendDetails(new OnboardingSession.ResendDetails(encrypted, null)));
        return new VerifyKeyResponse(domains);
    }

    public VerifyKeyResponse listDomainsFromSession(String sessionToken) {
        OnboardingSession session = onboardingSessionStore.get(sessionToken).requireResendDetails();
        String rawKey = SecurityUtil.decrypt(session.getResendDetails().encryptedResendApiKey(), encryptionProperties.key());
        return new VerifyKeyResponse(resendClient.listVerifiedDomains(rawKey));
    }

    public void addEmailProvider(String sessionToken, String apiKey, String fromAddress)
    {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new InvalidOnboardingSessionException();
        }
        OnboardingSession existing  = onboardingSessionStore.get(sessionToken).requireSchemaConfirmed();
        resendClient.validateApiKey(apiKey);
        String encryptedApiKey = SecurityUtil.encrypt(apiKey, encryptionProperties.key());
        OnboardingSession updated = existing.withResendDetails(new OnboardingSession.ResendDetails(encryptedApiKey, fromAddress));

        onboardingSessionStore.updateSession(sessionToken, updated);
    }

    public EmailProviderInfoResponse getEmailProviderInfo(UUID accountId) {
        EmailProvider provider = emailProviderRepository.findByAccountId(accountId)
                .orElseThrow(() -> new EmailProviderNotFoundException(accountId));
        String rawKey = SecurityUtil.decrypt(provider.getEncryptedApiKey(), encryptionProperties.key());
        String keyTail = rawKey.length() >= 4 ? rawKey.substring(rawKey.length() - 4) : rawKey;
        return new EmailProviderInfoResponse(provider.getFromAddress(), provider.getFromName(), keyTail);
    }

    public VerifyKeyResponse listDomains(UUID accountId) {
        EmailProvider provider = emailProviderRepository.findByAccountId(accountId)
                .orElseThrow(() -> new EmailProviderNotFoundException(accountId));
        String rawKey = SecurityUtil.decrypt(provider.getEncryptedApiKey(), encryptionProperties.key());
        return new VerifyKeyResponse(resendClient.listVerifiedDomains(rawKey));
    }

    public void reconfigure(UUID accountId, String apiKey, String fromName, String fromAddress) {
        resendClient.validateApiKey(apiKey);
        String encrypted = SecurityUtil.encrypt(apiKey, encryptionProperties.key());
        EmailProvider provider = emailProviderRepository.findByAccountId(accountId)
                .orElseThrow(() -> new EmailProviderNotFoundException(accountId));
        provider.setEncryptedApiKey(encrypted);
        provider.setFromAddress(fromAddress);
        provider.setFromName(fromName);
        emailProviderRepository.save(provider);
    }

    public void updateSender(UUID accountId, String fromName, String fromAddress) {
        EmailProvider provider = emailProviderRepository.findByAccountId(accountId)
                .orElseThrow(() -> new EmailProviderNotFoundException(accountId));
        provider.setFromAddress(fromAddress);
        provider.setFromName(fromName);
        emailProviderRepository.save(provider);
    }

    public void testResendConnection(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new InvalidOnboardingSessionException();
        }
        OnboardingSession session  = onboardingSessionStore.get(sessionToken).requireResendDetails();
        String decryptedApiKey = SecurityUtil.decrypt(session.getResendDetails().encryptedResendApiKey(), encryptionProperties.key());

        UUID rateLimitKey = session.getAccountId() != null
                ? session.getAccountId()
                : UUID.nameUUIDFromBytes(sessionToken.getBytes());

        resendClient.sendTestEmail(rateLimitKey,
                decryptedApiKey,
                session.getResendDetails().fromAddress(),
                session.getOwnerEmail(),
                "Test email from BroadcastMail",
                "<p>Your Resend integration is working correctly.</p>");
    }
}
