package com.broadcastmail.api.onboarding;

import com.broadcastmail.api.account.AccountCreationService;
import com.broadcastmail.api.connection.ConnectionService;
import com.broadcastmail.api.oauth.OAuthSessionStore;
import com.broadcastmail.api.onboarding.dto.AccountCreationResult;
import com.broadcastmail.api.supabase.management.SupabaseAccessTokens;
import com.broadcastmail.api.supabase.management.SupabaseManagementClient;
import com.broadcastmail.api.supabase.management.dto.SupabaseProject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;


@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingService {
    private final OAuthSessionStore onboardingSessionStore;
    private final AccountCreationService accountCreationService;
    private final ConnectionService connectionService;
    private final SessionReaderService sessionReaderService;
    private final TransactionTemplate transactionTemplate;
    private final SupabaseAccessTokens supabaseAccessTokens;
    private final SupabaseManagementClient supabaseManagementClient;

    public String completeOnboarding(String sessionToken) {
        onboardingSessionStore.get(sessionToken)
                .requireSchemaConfirmed()
                .requireEmailTableConfirmed()
                .requireResendDetails();
        // No-op when /schema/test already ran; otherwise provisions + verifies now. Also grants the final table set.
        OnboardingSession ready = sessionReaderService.ensureReady(sessionToken).requireConnectionVerified();
        OnboardingSession session = ready.withProjectName(resolveProjectName(ready));
        // Account and connection are saved together: a half-created account blocks every retry on the unique email.
        AccountCreationResult result = transactionTemplate.execute(status -> {
            AccountCreationResult created = accountCreationService.createFromOnboarding(session);
            connectionService.createConnection(
                    created.accountId(), session.connectionSpec(), session.emailSourceSpec(), session.columnSelection());
            return created;
        });
        return result.rawApiKey();
    }

    private String resolveProjectName(OnboardingSession session) {
        String ref = session.getProjectRef();
        try {
            String accessToken = supabaseAccessTokens.decrypt(session.getEncryptedAccessToken());
            return supabaseManagementClient.listProjects(accessToken).stream()
                    .filter(p -> ref.equals(p.ref()))
                    .map(SupabaseProject::name)
                    .findFirst()
                    .orElse(ref);
        } catch (Exception e) {
            log.warn("[onboarding] could not resolve project name for {}: {}", ref, e.getMessage());
            return ref;
        }
    }

    /** Provisions the reader role on first call and waits (up to ~15s) for the pooler to accept it. */
    public void testConnection(String sessionToken) {
        sessionReaderService.ensureReady(sessionToken);
    }
}
