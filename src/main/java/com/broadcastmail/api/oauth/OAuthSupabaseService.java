package com.broadcastmail.api.oauth;

import com.broadcastmail.api.account.AccountService;
import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.common.exceptions.NoSupabaseProjectsException;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.oauth.dto.OAuthCallbackResult;
import com.broadcastmail.api.onboarding.PartialOnboardingSession;
import com.broadcastmail.api.supabase.SupabaseManagementClient;
import com.broadcastmail.api.supabase.dto.SupabaseProject;
import com.broadcastmail.api.supabase.dto.SupabaseTokenResponse;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuthSupabaseService {
    private static final Logger log = LoggerFactory.getLogger(OAuthSupabaseService.class);

    private final SupabaseManagementClient supabaseManagementClient;
    private final OAuthSessionStore onboardingSessionStore;
    private final ConnectionRepository connectionRepository;
    private final AccountService accountService;
    private final OAuthSessionService oAuthSessionService;
    private final EncryptionProperties encryptionProperties;

    public OAuthCallbackResult exchangeWithPKCE(String code, String codeVerifier, @Nullable UUID accountId) {
        SupabaseTokenResponse tokenResponse = supabaseManagementClient.exchangeCodeForTokensPKCE(code, codeVerifier);
        String rawAccessToken = tokenResponse.accessToken();
        Instant tokenExpiresAt = Instant.now().plusSeconds(tokenResponse.expiresIn());
        String encryptedAccessToken = SecurityUtil.encrypt(rawAccessToken, encryptionProperties.key());
        String encryptedRefreshToken = SecurityUtil.encrypt(tokenResponse.refreshToken(), encryptionProperties.key());

        List<SupabaseProject> projects = supabaseManagementClient.listProjects(rawAccessToken);
        if (projects.isEmpty()) throw new NoSupabaseProjectsException();

        String ownerEmail = resolveOwnerEmail(rawAccessToken, projects.getFirst().ref());

        if (accountId != null) {
            return handleReconfigure(accountId, ownerEmail, encryptedAccessToken, encryptedRefreshToken, tokenExpiresAt, projects);
        }

        Optional<UUID> existingAccountId = projects.stream()
                .flatMap(p -> connectionRepository.findByProjectRef(p.ref()).stream())
                .map(Connection::getAccountId)
                .findFirst();

        if (existingAccountId.isPresent()) {
            String rawKey = accountService.rotateApiKey(existingAccountId.get());
            return new OAuthCallbackResult.ReturningUser(rawKey);
        }

        if (projects.size() == 1) {
            return oAuthSessionService.setupProjectAndCreateSession(
                    projects.getFirst().ref(), rawAccessToken, ownerEmail,
                    encryptedAccessToken, encryptedRefreshToken, tokenExpiresAt);
        }

        String partialToken = onboardingSessionStore.createPartial(
                PartialOnboardingSession.forNewUser(
                        ownerEmail, encryptedAccessToken, encryptedRefreshToken, tokenExpiresAt, projects));
        return new OAuthCallbackResult.NewUserMultipleProjects(partialToken);
    }

    private OAuthCallbackResult handleReconfigure(
            UUID accountId, String ownerEmail,
            String encryptedAccessToken, String encryptedRefreshToken,
            Instant tokenExpiresAt, List<SupabaseProject> projects) {

        if (projects.size() == 1) {
            String sessionToken = oAuthSessionService.setupReconfigureSession(
                    accountId, ownerEmail, projects.getFirst().ref(),
                    encryptedAccessToken, encryptedRefreshToken, tokenExpiresAt);
            return new OAuthCallbackResult.ReconfigureSingleProject(sessionToken);
        }

        String partialToken = onboardingSessionStore.createPartial(
                PartialOnboardingSession.forReconfigure(
                        accountId, ownerEmail, encryptedAccessToken, encryptedRefreshToken,
                        tokenExpiresAt, projects));
        return new OAuthCallbackResult.ReconfigureMultipleProjects(partialToken);
    }

    private String resolveOwnerEmail(String rawAccessToken, String projectRef) {
        try {
            return supabaseManagementClient.resolveOwnerEmail(rawAccessToken, projectRef);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("Could not resolve owner email from project {}: {} — {}", projectRef, e.getMessage(), cause.getMessage());
            return null;
        }
    }
}
