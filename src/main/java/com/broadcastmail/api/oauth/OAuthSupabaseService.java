package com.broadcastmail.api.oauth;

import com.broadcastmail.api.account.AccountService;
import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.common.exceptions.NoSupabaseProjectsException;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.oauth.dto.OAuthCallbackResult;
import com.broadcastmail.api.onboarding.PartialOnboardingSession;
import com.broadcastmail.api.supabase.management.SupabaseManagementClient;
import com.broadcastmail.api.supabase.management.dto.SupabaseProject;
import com.broadcastmail.api.supabase.management.dto.SupabaseTokenResponse;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuthSupabaseService {
    private final SupabaseManagementClient supabaseManagementClient;
    private final OAuthSessionStore onboardingSessionStore;
    private final ConnectionRepository connectionRepository;
    private final AccountService accountService;
    private final OAuthSessionService oAuthSessionService;
    private final EncryptionProperties encryptionProperties;

    public OAuthCallbackResult exchangeWithPKCE(String code, String codeVerifier, String redirectUri, @Nullable UUID accountId) {
        SupabaseTokenResponse tokenResponse = supabaseManagementClient.exchangeCodeForTokensPKCE(code, codeVerifier, redirectUri);
        String rawAccessToken = tokenResponse.accessToken();
        Instant tokenExpiresAt = Instant.now().plusSeconds(tokenResponse.expiresIn());
        String encryptedAccessToken = SecurityUtil.encrypt(rawAccessToken, encryptionProperties.key());
        String encryptedRefreshToken = SecurityUtil.encrypt(tokenResponse.refreshToken(), encryptionProperties.key());

        List<SupabaseProject> projects = supabaseManagementClient.listProjects(rawAccessToken);
        if (projects.isEmpty()) throw new NoSupabaseProjectsException();

        if (accountId != null) {
            return handleReconfigure(accountId, encryptedAccessToken, encryptedRefreshToken, tokenExpiresAt, projects);
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
                    projects.getFirst().ref(), null, projects.getFirst().poolerPrefix(),
                    encryptedAccessToken, encryptedRefreshToken, tokenExpiresAt);
        }

        String partialToken = onboardingSessionStore.createPartial(
                PartialOnboardingSession.forNewUser(
                        null, encryptedAccessToken, encryptedRefreshToken, tokenExpiresAt, projects));
        return new OAuthCallbackResult.NewUserMultipleProjects(partialToken);
    }

    private OAuthCallbackResult handleReconfigure(
            UUID accountId,
            String encryptedAccessToken, String encryptedRefreshToken,
            Instant tokenExpiresAt, List<SupabaseProject> projects) {

        if (projects.size() == 1) {
            String sessionToken = oAuthSessionService.setupReconfigureSession(
                    accountId, null, projects.getFirst().ref(), projects.getFirst().poolerPrefix(),
                    encryptedAccessToken, encryptedRefreshToken, tokenExpiresAt);
            return new OAuthCallbackResult.ReconfigureSingleProject(sessionToken);
        }

        String partialToken = onboardingSessionStore.createPartial(
                PartialOnboardingSession.forReconfigure(
                        accountId, null, encryptedAccessToken, encryptedRefreshToken,
                        tokenExpiresAt, projects));
        return new OAuthCallbackResult.ReconfigureMultipleProjects(partialToken);
    }
}
