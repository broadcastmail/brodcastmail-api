package com.broadcastmail.api.onboarding;

import com.broadcastmail.api.oauth.OAuthSessionStore;
import com.broadcastmail.api.supabase.reader.ReaderConnectionVerifier;
import com.broadcastmail.api.supabase.reader.ReaderRoleService;
import com.broadcastmail.api.supabase.management.SupabaseAccessTokens;
import com.broadcastmail.api.supabase.SupabaseSql.TableRef;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Makes sure an onboarding/reconfigure session has a working reader role on its project: provisions one on first use,
 * grants the tables selected so far, and verifies the pooler accepts the login (retrying while its secret cache
 * catches up). Idempotent — later calls only top up grants.
 */
@Service
@RequiredArgsConstructor
public class SessionReaderService {

    private final OAuthSessionStore onboardingSessionStore;
    private final ReaderRoleService readerRoleService;
    private final ReaderConnectionVerifier readerConnectionVerifier;
    private final SupabaseAccessTokens supabaseAccessTokens;

    public OnboardingSession ensureReady(String sessionToken) {
        OnboardingSession session = onboardingSessionStore.get(sessionToken);
        String accessToken = supabaseAccessTokens.decrypt(session.getEncryptedAccessToken());
        Set<TableRef> tables = selectedTables(session);

        if (!session.hasReader()) {
            ReaderRoleService.Provisioned provisioned =
                    readerRoleService.provision(accessToken, session.getProjectRef(), tables);
            session = session
                    .withDbRole(provisioned.role())
                    .withEncryptedRolePassword(provisioned.encryptedPassword())
                    .withConnectionVerified(false);
            // Persist before verifying so a retried request reuses this role instead of creating another.
            onboardingSessionStore.updateSession(sessionToken, session);
        } else {
            syncGrants(session, accessToken);
        }

        if (!session.isConnectionVerified()) {
            readerConnectionVerifier.verify(session.readerConnectionFactory(),
                    readerRoleService.decryptPassword(session.getEncryptedRolePassword()));
            session = session.withConnectionVerified(true);
            onboardingSessionStore.updateSession(sessionToken, session);
        }
        return session;
    }

    /** Syncs the role's grants to the session's selected tables, if the role exists yet. Instant — no verification. */
    public void grantSelected(OnboardingSession session) {
        if (!session.hasReader()) return;
        syncGrants(session, supabaseAccessTokens.decrypt(session.getEncryptedAccessToken()));
    }

    private void syncGrants(OnboardingSession session, String accessToken) {
        readerRoleService.syncGrants(accessToken, session.getProjectRef(), session.getDbRole(), selectedTables(session));
    }

    static Set<TableRef> selectedTables(OnboardingSession session) {
        OnboardingSession.SchemaDetails schema = session.getSchemaDetails();
        OnboardingSession.EmailTableDetails email = session.getEmailTableDetails();
        return ReaderRoleService.tables(
                schema != null ? schema.userSchema() : null,
                schema != null ? schema.userTable() : null,
                email != null ? email.emailTableSchema() : null,
                email != null ? email.emailTableName() : null);
    }
}
