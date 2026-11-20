package com.broadcastmail.api.oauth;

import com.broadcastmail.api.oauth.dto.OAuthCallbackResult;
import com.broadcastmail.api.oauth.dto.ProjectOption;
import com.broadcastmail.api.onboarding.OnboardingSession;
import com.broadcastmail.api.onboarding.PartialOnboardingSession;
import com.broadcastmail.api.supabase.SupabaseSql;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuthSessionService {
    private final OAuthSessionStore onboardingSessionStore;

    public OAuthCallbackResult.NewUserSingleProject setupProjectAndCreateSession(
            String projectRef,
            String ownerEmail,
            String encryptedAccessToken,
            String encryptedRefreshToken,
            Instant tokenExpiresAt) {

        String sessionToken = provisionSession(projectRef, OnboardingSession.builder()
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
            String encryptedAccessToken,
            String encryptedRefreshToken,
            Instant tokenExpiresAt) {

        OnboardingSession session = OnboardingSession.builder()
                .accountId(accountId)
                .ownerEmail(ownerEmail)
                .projectRef(projectRef)
                .projectUrl(SupabaseSql.buildProjectUrl(projectRef))
                .jdbcUrl(SupabaseSql.buildJdbcUrl(projectRef))
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

        OAuthCallbackResult.NewUserSingleProject result;
        if (partial.isReconfigure()) {
            String sessionToken = setupReconfigureSession(
                    partial.accountId(),
                    partial.ownerEmail(),
                    projectRef,
                    partial.encryptedAccessToken(),
                    partial.encryptedRefreshToken(),
                    partial.tokenExpiresAt());
            result = new OAuthCallbackResult.NewUserSingleProject(sessionToken);
        } else {
            result = setupProjectAndCreateSession(
                    projectRef,
                    partial.ownerEmail(),
                    partial.encryptedAccessToken(),
                    partial.encryptedRefreshToken(),
                    partial.tokenExpiresAt());
        }

        onboardingSessionStore.invalidatePartial(partialSessionToken);
        return result;
    }
    private String provisionSession(
            String projectRef,
            OnboardingSession.OnboardingSessionBuilder builder) {

        String jdbcUrl = SupabaseSql.buildJdbcUrl(projectRef);
        String projectUrl = SupabaseSql.buildProjectUrl(projectRef);

        OnboardingSession session = builder
                .projectRef(projectRef)
                .projectUrl(projectUrl)
                .jdbcUrl(jdbcUrl)
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();

        return onboardingSessionStore.create(session);
    }

}
