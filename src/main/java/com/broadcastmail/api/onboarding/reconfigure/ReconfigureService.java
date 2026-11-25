package com.broadcastmail.api.onboarding.reconfigure;

import com.broadcastmail.api.campaign.CampaignInProgressGuard;
import com.broadcastmail.api.onboarding.OnboardingSession;
import com.broadcastmail.api.onboarding.SessionReaderService;
import com.broadcastmail.api.account.AccountService;
import com.broadcastmail.api.common.exceptions.InvalidOnboardingSessionException;
import com.broadcastmail.api.connection.ConnectionService;
import com.broadcastmail.api.oauth.OAuthSessionStore;
import com.broadcastmail.api.supabase.reader.ReaderRoleService;
import com.broadcastmail.api.supabase.management.ProjectNameResolver;
import com.broadcastmail.api.supabase.management.SupabaseAccessTokens;
import com.broadcastmail.api.oauth.token.OAuthTokenService;
import com.broadcastmail.common.connection.Connection;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReconfigureService {

    private final OAuthSessionStore onboardingSessionStore;
    private final SessionReaderService sessionReaderService;
    private final ConnectionService connectionService;
    private final ReaderRoleService readerRoleService;
    private final SupabaseAccessTokens supabaseAccessTokens;
    private final OAuthTokenService oAuthTokenService;
    private final AccountService accountService;
    private final ProjectNameResolver projectNameResolver;
    private final CampaignInProgressGuard campaignInProgressGuard;

    @Transactional
    public void reconfigure(String sessionToken) {
        OnboardingSession pending = onboardingSessionStore.get(sessionToken).requireSchemaConfirmed();
        if (!pending.isReconfigure()) {
            throw new InvalidOnboardingSessionException();
        }
        campaignInProgressGuard.requireNoneInProgress(pending.getAccountId());

        Connection connection = connectionService.getByAccountId(pending.getAccountId());
        String oldProjectRef = connection.getProjectRef();
        String oldRole = connection.getDbRole();

        // Same project → the session reuses the connection's role (no password change, so no pooler lag);
        // new project → /schema/test already provisioned a fresh role there. Either way it's verified here.
        OnboardingSession session = sessionReaderService.ensureReady(sessionToken).requireConnectionVerified();

        String projectName = projectNameResolver.resolve(
                session.getProjectRef(), () -> supabaseAccessTokens.decrypt(session.getEncryptedAccessToken()));
        connectionService.reconfigure(
                connection, session.withProjectName(projectName).connectionSpec(), session.columnSelection());
        // Email table is kept on reconfigure, so grants cover the new profile table plus the existing email table.
        readerRoleService.handOver(supabaseAccessTokens.decrypt(session.getEncryptedAccessToken()),
                oldProjectRef, oldRole, connection.getProjectRef(), connection.getDbRole(),
                ConnectionService.tablesOf(connection));

        oAuthTokenService.replace(session.getAccountId(), session.getEncryptedAccessToken(),
                session.getEncryptedRefreshToken(), session.getTokenExpiresAt());
        if (session.getOwnerEmail() != null) {
            accountService.updateOwnerEmail(session.getAccountId(), session.getOwnerEmail());
        }

        onboardingSessionStore.invalidate(sessionToken);
    }
}
