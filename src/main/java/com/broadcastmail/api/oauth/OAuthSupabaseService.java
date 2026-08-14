package com.broadcastmail.api.oauth;

import com.broadcastmail.api.account.AccountService;
import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.common.exceptions.NoSupabaseProjectsException;
import com.broadcastmail.api.common.exceptions.OAuthStateValidationException;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.oauth.dto.OAuthCallbackResult;
import com.broadcastmail.api.onboarding.OnboardingSession;
import com.broadcastmail.api.onboarding.PartialOnboardingSession;
import com.broadcastmail.api.onboarding.OnboardingSessionStore;
import com.broadcastmail.api.supabase.SupabaseManagementClient;
import com.broadcastmail.api.supabase.SupabaseSql;
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

@Service
@RequiredArgsConstructor
public class OAuthSupabaseService {

    private final OAuthStateStore oAuthStateStore;
    private final SupabaseManagementClient supabaseManagementClient;
    private final OnboardingSessionStore onboardingSessionStore;
    private final AccountRepository accountRepository;
    private final AccountService accountService;

    @Value("${supabase.oauth.client-id}")
    private String clientId;

    @Value("${supabase.oauth.redirect-uri}")
    private String redirectUri;

    private final EncryptionProperties encryptionProperties;


    public String buildAuthorizationUrl() {
        String state = oAuthStateStore.generateAndStore();
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
        if (!oAuthStateStore.validate(state)) {
            throw new OAuthStateValidationException();
        }

        SupabaseTokenResponse tokenResponse = supabaseManagementClient.exchangeCodeForTokens(code);
        String rawAccessToken = tokenResponse.accessToken();
        Instant tokenExpiresAt = Instant.now().plusSeconds(tokenResponse.expiresIn());

        String encryptedAccessToken = SecurityUtil.encrypt(rawAccessToken, encryptionProperties.key());
        String encryptedRefreshToken = SecurityUtil.encrypt(tokenResponse.refreshToken(), encryptionProperties.key());

        String ownerEmail = supabaseManagementClient.getOwnerEmail(rawAccessToken);

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
            return setupProjectAndCreateSession(
                    projectRef, rawAccessToken, ownerEmail,
                    encryptedAccessToken, encryptedRefreshToken, tokenExpiresAt);
        } else {
            String partialToken = onboardingSessionStore.createPartial(
                    ownerEmail,
                    encryptedAccessToken,
                    encryptedRefreshToken,
                    tokenExpiresAt
            );
            return new OAuthCallbackResult.NewUserMultipleProjects(projects, partialToken);
        }
    }

    public OAuthCallbackResult.NewUserSingleProject  selectProject(String projectRef, String partialSessionToken) {
        PartialOnboardingSession partial = onboardingSessionStore.getPartial(partialSessionToken);
        String rawAccessToken = SecurityUtil.decrypt(partial.encryptedAccessToken(), encryptionProperties.key());

        OAuthCallbackResult.NewUserSingleProject result = setupProjectAndCreateSession(
                projectRef, rawAccessToken,
                partial.ownerEmail(),
                partial.encryptedAccessToken(),
                partial.encryptedRefreshToken(),
                partial.tokenExpiresAt());

        onboardingSessionStore.invalidatePartial(partialSessionToken);
        return result;
    }

    private OAuthCallbackResult.NewUserSingleProject setupProjectAndCreateSession(
            String projectRef,
            String rawAccessToken,
            String ownerEmail,
            String encryptedAccessToken,
            String encryptedRefreshToken,
            Instant tokenExpiresAt) {

        String rolePassword = SecurityUtil.generatePassword();
        String createRoleSql = SupabaseSql.CREATE_READER_ROLE.formatted(rolePassword);
        supabaseManagementClient.executeSql(rawAccessToken, projectRef, createRoleSql);

        String jdbcUrl = "jdbc:postgresql://db." + projectRef + ".supabase.co:5432/postgres";
        String projectUrl = "https://" + projectRef + ".supabase.co";

        OnboardingSession session = OnboardingSession.builder()
                .projectRef(projectRef)
                .projectUrl(projectUrl)
                .jdbcUrl(jdbcUrl)
                .encryptedRolePassword(SecurityUtil.encrypt(rolePassword, encryptionProperties.key()))
                .ownerEmail(ownerEmail)
                .encryptedAccessToken(encryptedAccessToken)
                .encryptedRefreshToken(encryptedRefreshToken)
                .tokenExpiresAt(tokenExpiresAt)
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();

        String sessionToken = onboardingSessionStore.create(session);
        return new OAuthCallbackResult.NewUserSingleProject(sessionToken);
    }
}