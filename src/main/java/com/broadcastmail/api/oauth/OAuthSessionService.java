package com.broadcastmail.api.oauth;

import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.oauth.dto.OAuthCallbackResult;
import com.broadcastmail.api.oauth.dto.ProjectOption;
import com.broadcastmail.api.onboarding.OnboardingSession;
import com.broadcastmail.api.onboarding.PartialOnboardingSession;
import com.broadcastmail.api.supabase.management.SupabaseManagementClient;
import com.broadcastmail.api.supabase.SupabaseSql;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuthSessionService {
    private final OAuthSessionStore onboardingSessionStore;
    private final SupabaseManagementClient supabaseManagementClient;
    private final EncryptionProperties encryptionProperties;
    private final ConnectionRepository connectionRepository;

    public OAuthCallbackResult.NewUserSingleProject setupProjectAndCreateSession(
            String projectRef,
            String ownerEmail,
            String poolerPrefix,
            String encryptedAccessToken,
            String encryptedRefreshToken,
            Instant tokenExpiresAt) {

        String sessionToken = provisionSession(projectRef, poolerPrefix, OnboardingSession.builder()
                .ownerEmail(ownerEmail)
                .encryptedAccessToken(encryptedAccessToken)
                .encryptedRefreshToken(encryptedRefreshToken)
                .tokenExpiresAt(tokenExpiresAt));

        return new OAuthCallbackResult.NewUserSingleProject(sessionToken);
    }

    public String setupReconfigureSession(
            UUID accountId,
            String ownerEmail,
            String projectRef,
            String poolerPrefix,
            String encryptedAccessToken,
            String encryptedRefreshToken,
            Instant tokenExpiresAt) {

        // Reuse the existing role only on the same project — on another project it doesn't exist, and /schema/test
        // provisions a new one. Never re-password it: Supavisor caches secrets, so a rotated password fails for a while.
        Optional<Connection> sameProject = connectionRepository.findByAccountId(accountId)
                .filter(c -> projectRef.equals(c.getProjectRef()));

        OnboardingSession session = OnboardingSession.builder()
                .accountId(accountId)
                .ownerEmail(ownerEmail)
                .projectRef(projectRef)
                .projectUrl(SupabaseSql.buildProjectUrl(projectRef))
                .poolerPrefix(poolerPrefix)
                .dbRole(sameProject.map(Connection::getDbRole).orElse(null))
                .encryptedRolePassword(sameProject.map(Connection::getEncryptedCreds).orElse(null))
                .encryptedAccessToken(encryptedAccessToken)
                .encryptedRefreshToken(encryptedRefreshToken)
                .tokenExpiresAt(tokenExpiresAt)
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();

        return onboardingSessionStore.create(session);
    }

    public List<ProjectOption> listPartialProjects(String partialToken) {
        PartialOnboardingSession partial = onboardingSessionStore.getPartial(partialToken);

        return partial.projects().stream()
                .map(project -> new ProjectOption(
                        project.ref(),
                        project.name(),
                        project.status()
                ))
                .toList();
    }

    public OAuthCallbackResult.NewUserSingleProject selectProject(
            String projectRef, String partialSessionToken) {
        PartialOnboardingSession partial = onboardingSessionStore.getPartial(partialSessionToken);

        String rawAccessToken = SecurityUtil.decrypt(partial.encryptedAccessToken(), encryptionProperties.key());
        String poolerPrefix = supabaseManagementClient.getPoolerPrefix(rawAccessToken, projectRef);

        OAuthCallbackResult.NewUserSingleProject result;
        if (partial.isReconfigure()) {
            String sessionToken = setupReconfigureSession(
                    partial.accountId(),
                    partial.ownerEmail(),
                    projectRef,
                    poolerPrefix,
                    partial.encryptedAccessToken(),
                    partial.encryptedRefreshToken(),
                    partial.tokenExpiresAt());
            result = new OAuthCallbackResult.NewUserSingleProject(sessionToken);
        } else {
            result = setupProjectAndCreateSession(
                    projectRef,
                    partial.ownerEmail(),
                    poolerPrefix,
                    partial.encryptedAccessToken(),
                    partial.encryptedRefreshToken(),
                    partial.tokenExpiresAt());
        }

        onboardingSessionStore.invalidatePartial(partialSessionToken);
        return result;
    }

    private String provisionSession(
            String projectRef,
            String poolerPrefix,
            OnboardingSession.OnboardingSessionBuilder builder) {

        OnboardingSession session = builder
                .projectRef(projectRef)
                .projectUrl(SupabaseSql.buildProjectUrl(projectRef))
                .poolerPrefix(poolerPrefix)
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();

        return onboardingSessionStore.create(session);
    }

}
