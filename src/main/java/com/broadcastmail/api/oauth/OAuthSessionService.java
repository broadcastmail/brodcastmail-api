package com.broadcastmail.api.oauth;

import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.oauth.dto.OAuthCallbackResult;
import com.broadcastmail.api.oauth.dto.ProjectOption;
import com.broadcastmail.api.onboarding.OnboardingSession;
import com.broadcastmail.api.onboarding.PartialOnboardingSession;
import com.broadcastmail.api.supabase.SupabaseManagementClient;
import com.broadcastmail.api.supabase.SupabaseSql;
import com.broadcastmail.api.supabase.dto.SupabaseProject;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
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
            String rawAccessToken,
            String encryptedAccessToken,
            String encryptedRefreshToken,
            Instant tokenExpiresAt) {

        String sessionToken = provisionSession(projectRef, poolerPrefix, rawAccessToken, OnboardingSession.builder()
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
            String rawAccessToken,
            String encryptedAccessToken,
            String encryptedRefreshToken,
            Instant tokenExpiresAt) {

        String encryptedRolePassword = connectionRepository.findByAccountId(accountId)
                .map(Connection::getEncryptedCreds)
                .orElseGet(() -> {
                    String pw = SecurityUtil.generatePassword();
                    supabaseManagementClient.executeSql(rawAccessToken, projectRef,
                            SupabaseSql.PROVISION_READER.formatted(pw, pw));
                    return SecurityUtil.encrypt(pw, encryptionProperties.key());
                });

        OnboardingSession session = OnboardingSession.builder()
                .accountId(accountId)
                .ownerEmail(ownerEmail)
                .projectRef(projectRef)
                .projectUrl(SupabaseSql.buildProjectUrl(projectRef))
                .jdbcUrl(SupabaseSql.buildPoolerJdbcUrl(projectRef, poolerPrefix))
                .jdbcUsername(SupabaseSql.poolerReaderRole(projectRef))
                .encryptedAccessToken(encryptedAccessToken)
                .encryptedRefreshToken(encryptedRefreshToken)
                .encryptedRolePassword(encryptedRolePassword)
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
        String poolerPrefix = partial.projects().stream()
                .filter(p -> p.ref().equals(projectRef))
                .findFirst()
                .map(SupabaseProject::poolerPrefix)
                .orElseGet(() -> supabaseManagementClient.getPoolerPrefix(rawAccessToken, projectRef));

        OAuthCallbackResult.NewUserSingleProject result;
        if (partial.isReconfigure()) {
            String sessionToken = setupReconfigureSession(
                    partial.accountId(),
                    partial.ownerEmail(),
                    projectRef,
                    poolerPrefix,
                    rawAccessToken,
                    partial.encryptedAccessToken(),
                    partial.encryptedRefreshToken(),
                    partial.tokenExpiresAt());
            result = new OAuthCallbackResult.NewUserSingleProject(sessionToken);
        } else {
            result = setupProjectAndCreateSession(
                    projectRef,
                    partial.ownerEmail(),
                    poolerPrefix,
                    rawAccessToken,
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
            String rawAccessToken,
            OnboardingSession.OnboardingSessionBuilder builder) {

        String rolePassword = SecurityUtil.generatePassword();
        supabaseManagementClient.executeSql(rawAccessToken, projectRef,
                SupabaseSql.PROVISION_READER.formatted(rolePassword, rolePassword));

        OnboardingSession session = builder
                .projectRef(projectRef)
                .projectUrl(SupabaseSql.buildProjectUrl(projectRef))
                .jdbcUrl(SupabaseSql.buildPoolerJdbcUrl(projectRef, poolerPrefix))
                .jdbcUsername(SupabaseSql.poolerReaderRole(projectRef))
                .encryptedRolePassword(SecurityUtil.encrypt(rolePassword, encryptionProperties.key()))
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();

        return onboardingSessionStore.create(session);
    }

}
