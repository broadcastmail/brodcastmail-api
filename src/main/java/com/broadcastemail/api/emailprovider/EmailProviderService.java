package com.broadcastemail.api.emailprovider;

import com.broadcastemail.api.common.SecurityUtil;
import com.broadcastemail.api.common.exceptions.InvalidOnboardingSessionException;
import com.broadcastemail.api.config.EncryptionProperties;
import com.broadcastemail.api.onboarding.OnboardingSession;
import com.broadcastemail.api.onboarding.OnboardingSessionStore;
import com.broadcastemail.api.resend.ResendClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailProviderService {

    private final OnboardingSessionStore onboardingSessionStore;
    private final ResendClient resendClient;
    private final EncryptionProperties encryptionProperties;


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

}
