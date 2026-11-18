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
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
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
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final OAuthSessionService oAuthSessionService;
    private final EncryptionProperties encryptionProperties;

    public OAuthCallbackResult exchangeWithPKCE(String code, String codeVerifier, @Nullable UUID accountId) {
        SupabaseTokenResponse tokenResponse = supabaseManagementClient.exchangeCodeForTokensPKCE(code, codeVerifier);
        String rawAccessToken = tokenResponse.accessToken();
        Instant tokenExpiresAt = Instant.now().plusSeconds(tokenResponse.expiresIn());
        String encryptedAccessToken = SecurityUtil.encrypt(rawAccessToken, encryptionProperties.key());
        String encryptedRefreshToken = SecurityUtil.encrypt(tokenResponse.refreshToken(), encryptionProperties.key());
        String ownerEmail = supabaseManagementClient.getOwnerEmail(rawAccessToken);

        if (accountId != null) {
            return handleReconfigure(accountId, ownerEmail, rawAccessToken,
                    encryptedAccessToken, encryptedRefreshToken, tokenExpiresAt);
        }

        Optional<Account> existing = accountRepository.findByEmail(ownerEmail);
        if (existing.isPresent()) {
            String rawKey = accountService.rotateApiKey(existing.get().getId());
            return new OAuthCallbackResult.ReturningUser(rawKey);
        }

        List<SupabaseProject> projects = supabaseManagementClient.listProjects(rawAccessToken);
        if (projects.isEmpty()) throw new NoSupabaseProjectsException();

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
            UUID accountId, String ownerEmail, String rawAccessToken,
            String encryptedAccessToken, String encryptedRefreshToken, Instant tokenExpiresAt) {

        List<SupabaseProject> projects = supabaseManagementClient.listProjects(rawAccessToken);
        if (projects.isEmpty()) throw new NoSupabaseProjectsException();

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
}
