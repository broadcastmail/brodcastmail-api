package com.broadcastmail.api.onboarding;

import com.broadcastmail.api.account.AccountCreationService;
import com.broadcastmail.api.connection.ConnectionService;
import com.broadcastmail.api.oauth.OAuthSessionStore;
import com.broadcastmail.api.onboarding.dto.AccountCreationResult;
import com.broadcastmail.api.supabase.management.SupabaseAccessTokens;
import com.broadcastmail.api.supabase.management.ProjectNameResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;


@Service
@RequiredArgsConstructor
public class OnboardingService {
    private final OAuthSessionStore onboardingSessionStore;
    private final AccountCreationService accountCreationService;
    private final ConnectionService connectionService;
    private final SessionReaderService sessionReaderService;
    private final TransactionTemplate transactionTemplate;
    private final SupabaseAccessTokens supabaseAccessTokens;
    private final ProjectNameResolver projectNameResolver;

    public String completeOnboarding(String sessionToken) {
        onboardingSessionStore.get(sessionToken)
                .requireSchemaConfirmed()
                .requireEmailTableConfirmed()
                .requireResendDetails();
        // No-op when /schema/test already ran; otherwise provisions + verifies now. Also grants the final table set.
        OnboardingSession ready = sessionReaderService.ensureReady(sessionToken).requireConnectionVerified();
        OnboardingSession session = ready.withProjectName(projectNameResolver.resolve(
                ready.getProjectRef(), () -> supabaseAccessTokens.decrypt(ready.getEncryptedAccessToken())));
        // Account and connection are saved together: a half-created account blocks every retry on the unique email.
        AccountCreationResult result = transactionTemplate.execute(status -> {
            AccountCreationResult created = accountCreationService.createFromOnboarding(session);
            connectionService.createConnection(
                    created.accountId(), session.connectionSpec(), session.emailSourceSpec(), session.columnSelection());
            return created;
        });
        return result.rawApiKey();
    }

    /** Provisions the reader role on first call and waits (up to ~15s) for the pooler to accept it. */
    public void testConnection(String sessionToken) {
        sessionReaderService.ensureReady(sessionToken);
    }
}
