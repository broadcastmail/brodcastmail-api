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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuthSessionService {
    private final SupabaseManagementClient supabaseManagementClient;
    private final OAuthSessionStore onboardingSessionStore;
    private final EncryptionProperties encryptionProperties;

    public OAuthCallbackResult.NewUserSingleProject setupProjectAndCreateSession(
            String projectRef,
            String rawAccessToken,
            String ownerEmail,
            String encryptedAccessToken,
            String encryptedRefreshToken,
            Instant tokenExpiresAt) {

        String sessionToken = provisionSession(rawAccessToken, projectRef, OnboardingSession.builder()
                .ownerEmail(ownerEmail)
                .encryptedAccessToken(encryptedAccessToken)
                .encryptedRefreshToken(encryptedRefreshToken)
                .tokenExpiresAt(tokenExpiresAt));

        return new OAuthCallbackResult.NewUserSingleProject(sessionToken);
    }

    public String setupReconfigureSession (
            UUID accountId,
            String projectRef,
            String rawAccessToken,
            String encryptedAccessToken,
            String encryptedRefreshToken,
            Instant tokenExpiresAt) {

        return provisionSession(rawAccessToken, projectRef, OnboardingSession.builder()
                .accountId(accountId)
                .encryptedAccessToken(encryptedAccessToken)
                .encryptedRefreshToken(encryptedRefreshToken)
                .tokenExpiresAt(tokenExpiresAt));
    }

    /**
     * Projects for the /onboarding/select-project picker. userCount is fetched
     * here (query time), not in handleCallback() — keeps the OAuth redirect
     * itself to one purpose and doesn't add N external calls to that critical
     * path for a number the picker screen can afford to wait a beat for.
     */
    public List<ProjectOption> listPartialProjects(String partialToken) {
        PartialOnboardingSession partial = onboardingSessionStore.getPartial(partialToken);
        String rawAccessToken = decryptAccessToken(partial);

        return partial.projects().stream()
                .map(project -> new ProjectOption(
                        project.ref(),
                        project.name(),
                        project.status(),
                        fetchUserCount(rawAccessToken, project)
                ))
                .toList();
    }

    public OAuthCallbackResult.NewUserSingleProject selectProject(
            String projectRef, String partialSessionToken) {
        PartialOnboardingSession partial = onboardingSessionStore.getPartial(partialSessionToken);
        String rawAccessToken = SecurityUtil.decrypt(
                partial.encryptedAccessToken(), encryptionProperties.key());

        OAuthCallbackResult.NewUserSingleProject result = setupProjectAndCreateSession(
                projectRef, rawAccessToken,
                partial.ownerEmail(),
                partial.encryptedAccessToken(),
                partial.encryptedRefreshToken(),
                partial.tokenExpiresAt());

        onboardingSessionStore.invalidatePartial(partialSessionToken);
        return result;
    }
    private String provisionSession(
            String rawAccessToken,
            String projectRef,
            OnboardingSession.OnboardingSessionBuilder builder) {

        String rolePassword = SecurityUtil.generatePassword();
        String createRoleSql = SupabaseSql.CREATE_READER_ROLE.formatted(rolePassword);
        supabaseManagementClient.executeSql(rawAccessToken, projectRef, createRoleSql);

        String jdbcUrl = "jdbc:postgresql://db." + projectRef + ".supabase.co:5432/postgres";
        String projectUrl = "https://" + projectRef + ".supabase.co";

        OnboardingSession session = builder
                .projectRef(projectRef)
                .projectUrl(projectUrl)
                .jdbcUrl(jdbcUrl)
                .encryptedRolePassword(SecurityUtil.encrypt(rolePassword, encryptionProperties.key()))
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();

        return onboardingSessionStore.create(session);
    }

    private String decryptAccessToken(PartialOnboardingSession partial) {
        return SecurityUtil.decrypt(partial.encryptedAccessToken(), encryptionProperties.key());
    }

    private Integer fetchUserCount(String accessToken, SupabaseProject project) {
        if (!"ACTIVE_HEALTHY".equals(project.status())) {
            return null; // paused/restoring/etc. — querying it would just fail
        }
        try {
            return supabaseManagementClient.executeSqlQuery(
                            accessToken, project.ref(), SupabaseSql.COUNT_AUTH_USERS)
                    .stream()
                    .findFirst()
                    .map(row -> row.get("count"))
                    .map(count -> Integer.valueOf(count.toString()))
                    .orElse(null);
        } catch (RestClientException _) {
            // best-effort — one project's count failing shouldn't break the whole picker
            return null;
        }
    }
}
