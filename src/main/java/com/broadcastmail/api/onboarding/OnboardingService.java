package com.broadcastmail.api.onboarding;

import com.broadcastmail.api.account.AccountCreationService;
import com.broadcastmail.api.connection.ConnectionService;
import com.broadcastmail.common.account.Account;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class OnboardingService {
    private final OnboardingSessionStore onboardingSessionStore;
    private final AccountCreationService accountCreationService;
    private final ConnectionService connectionService;

    public void completeOnboarding(String sessionToken) {
        OnboardingSession session = onboardingSessionStore.get(sessionToken)
                .requireSchemaConfirmed()
                .requireResendDetails();
        Account account = accountCreationService.createFromOnboarding(session);
        connectionService.createConnection(account.getId(), session);
    }
}
