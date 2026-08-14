package com.broadcastmail.api.onboarding;

import com.broadcastmail.api.account.AccountCreationService;
import com.broadcastmail.api.connection.ConnectionService;
import com.broadcastmail.api.onboarding.dto.AccountCreationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class OnboardingService {
    private final OnboardingSessionStore onboardingSessionStore;
    private final AccountCreationService accountCreationService;
    private final ConnectionService connectionService;

    public String completeOnboarding(String sessionToken) {
        OnboardingSession session = onboardingSessionStore.get(sessionToken)
                .requireSchemaConfirmed()
                .requireResendDetails();
        AccountCreationResult result = accountCreationService.createFromOnboarding(session);
        connectionService.createConnection(result.accountId(), session);
        return result.rawApiKey();
    }
}
