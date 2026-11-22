package com.broadcastmail.api.onboarding;

import com.broadcastmail.api.account.AccountCreationService;
import com.broadcastmail.api.connection.ConnectionService;
import com.broadcastmail.api.oauth.OAuthSessionStore;
import com.broadcastmail.api.onboarding.dto.AccountCreationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class OnboardingService {
    private final OAuthSessionStore onboardingSessionStore;
    private final AccountCreationService accountCreationService;
    private final ConnectionService connectionService;
    private final SessionReaderService sessionReaderService;

    public String completeOnboarding(String sessionToken) {
        onboardingSessionStore.get(sessionToken)
                .requireSchemaConfirmed()
                .requireEmailTableConfirmed()
                .requireResendDetails();
        // No-op when /schema/test already ran; otherwise provisions + verifies now. Also grants the final table set.
        OnboardingSession session = sessionReaderService.ensureReady(sessionToken).requireConnectionVerified();
        AccountCreationResult result = accountCreationService.createFromOnboarding(session);
        connectionService.createConnection(
                result.accountId(), session.connectionSpec(), session.emailSourceSpec(), session.columnSelection());
        return result.rawApiKey();
    }

    /** Provisions the reader role on first call and waits (up to ~15s) for the pooler to accept it. */
    public void testConnection(String sessionToken) {
        sessionReaderService.ensureReady(sessionToken);
    }
}
