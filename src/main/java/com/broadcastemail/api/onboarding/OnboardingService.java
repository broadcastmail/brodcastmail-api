package com.broadcastemail.api.onboarding;

import com.broadcastemail.api.account.Account;
import com.broadcastemail.api.account.AccountCreationService;
import com.broadcastemail.api.connection.ConnectionService;
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
