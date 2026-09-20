package com.broadcastmail.api.oauth;

import com.broadcastmail.api.account.AccountService;
import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.common.exceptions.AccountNotFoundException;
import com.broadcastmail.api.common.exceptions.NoSupabaseProjectsException;
import com.broadcastmail.api.common.exceptions.OAuthStateValidationException;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.oauth.dto.OAuthCallbackResult;
import com.broadcastmail.api.onboarding.PartialOnboardingSession;
import com.broadcastmail.api.supabase.SupabaseManagementClient;
import com.broadcastmail.api.supabase.dto.SupabaseProject;
import com.broadcastmail.api.supabase.dto.SupabaseTokenResponse;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuthSupabaseService {

    private final OAuthStateStore oAuthStateStore;
    private final SupabaseManagementClient supabaseManagementClient;
    private final OAuthSessionStore onboardingSessionStore;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final OAuthSessionService oAuthSessionService;

    @Value("${supabase.oauth.client-id}")
    private String clientId;

    @Value("${supabase.oauth.redirect-uri}")
    private String redirectUri;

    private final EncryptionProperties encryptionProperties;


    public String buildAuthorizationUrl() {
        String state = oAuthStateStore.generateAndStore(null);
        return UriComponentsBuilder
                .fromUriString("https://api.supabase.com/v1/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "projects:read database:write")
                .queryParam("state", state)
                .toUriString();
    }

    public String buildReconfigureAuthorizationUrl(String sessionCookie) {
        String hashedKey = SecurityUtil.sha256(sessionCookie);
        UUID accountId = accountRepository.findByApiKeyHash(hashedKey)
                .map(Account::getId)
                .orElseThrow(AccountNotFoundException::new);
        String state = oAuthStateStore.generateAndStore(accountId);

        return UriComponentsBuilder
                .fromUriString("https://api.supabase.com/v1/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "projects:read database:write")
                .queryParam("state", state)
                .toUriString();
    }

    public OAuthCallbackResult handleCallback(String code, String state) {
        OAuthStateStore.ValidationResult validationResult = oAuthStateStore.validateAndGet(state);
        if (!validationResult.valid()) {
            throw new OAuthStateValidationException();
        }

        SupabaseTokenResponse tokenResponse = supabaseManagementClient.exchangeCodeForTokens(code);
        String rawAccessToken = tokenResponse.accessToken();
        Instant tokenExpiresAt = Instant.now().plusSeconds(tokenResponse.expiresIn());

        String encryptedAccessToken = SecurityUtil.encrypt(rawAccessToken, encryptionProperties.key());
        String encryptedRefreshToken = SecurityUtil.encrypt(tokenResponse.refreshToken(), encryptionProperties.key());

        String ownerEmail = supabaseManagementClient.getOwnerEmail(rawAccessToken);

        // If the state was associated with an accountId, it means this is a reconnect flow
        if (validationResult.accountId() != null) {
            return handleReconfigure(validationResult.accountId(), rawAccessToken,
                    encryptedAccessToken, encryptedRefreshToken, tokenExpiresAt);
        }

        // Else this is a new user flow
        Optional<Account> existingAccount = accountRepository.findByEmail(ownerEmail);
        if (existingAccount.isPresent()) {
            String rawKey = accountService.rotateApiKey(existingAccount.get().getId());
            return new OAuthCallbackResult.ReturningUser(rawKey);
        }

        List<SupabaseProject> projects = supabaseManagementClient.listProjects(rawAccessToken);
        if (projects.isEmpty()) {
            throw new NoSupabaseProjectsException();
        }

        if (projects.size() == 1) {
            String projectRef = projects.get(0).ref();
            return oAuthSessionService.setupProjectAndCreateSession(
                    projectRef, rawAccessToken, ownerEmail,
                    encryptedAccessToken, encryptedRefreshToken, tokenExpiresAt);
        } else {
            String partialToken = onboardingSessionStore.createPartial(
                    PartialOnboardingSession.forNewUser(
                            ownerEmail, encryptedAccessToken, encryptedRefreshToken,
                            tokenExpiresAt, projects)
            );
            return new OAuthCallbackResult.NewUserMultipleProjects(partialToken);
        }
    }

    private OAuthCallbackResult handleReconfigure(
            UUID accountId,
            String rawAccessToken,
            String encryptedAccessToken,
            String encryptedRefreshToken,
            Instant tokenExpiresAt) {

        List<SupabaseProject> projects = supabaseManagementClient.listProjects(rawAccessToken);
        if (projects.isEmpty()) {
            throw new NoSupabaseProjectsException();
        }

        if (projects.size() == 1) {
            String projectRef = projects.getFirst().ref();
            String sessionToken = oAuthSessionService.setupReconfigureSession(
                    accountId, projectRef, rawAccessToken,
                    encryptedAccessToken, encryptedRefreshToken, tokenExpiresAt);
            return new OAuthCallbackResult.ReconfigureSingleProject(sessionToken);
        } else {
            String partialToken = onboardingSessionStore.createPartial(
                    PartialOnboardingSession.forReconfigure(
                            accountId,
                            encryptedAccessToken,
                            encryptedRefreshToken,
                            tokenExpiresAt,
                            projects
                    )
            );
            return new OAuthCallbackResult.ReconfigureMultipleProjects(partialToken);
        }
    }
}