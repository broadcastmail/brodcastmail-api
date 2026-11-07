package com.broadcastmail.api.oauth;

import com.broadcastmail.api.account.AccountService;
import com.broadcastmail.api.common.exceptions.NoSupabaseProjectsException;
import com.broadcastmail.api.common.exceptions.OAuthStateValidationException;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.oauth.dto.OAuthCallbackResult;
import com.broadcastmail.api.supabase.SupabaseManagementClient;
import com.broadcastmail.api.supabase.dto.SupabaseProject;
import com.broadcastmail.api.supabase.dto.SupabaseTokenResponse;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthSupabaseServiceTest {

    private static final String ENCRYPTION_KEY = "test-encryption-key-32-chars-okk";
    private static final UUID ACCOUNT_ID = UUID.randomUUID();

    @Mock
    private OAuthStateStore oAuthStateStore;
    @Mock
    private SupabaseManagementClient supabaseManagementClient;
    @Mock
    private OAuthSessionStore onboardingSessionStore;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AccountService accountService;
    @Mock
    private OAuthSessionService oAuthSessionService;

    private OAuthSupabaseService oAuthSupabaseService;

    @BeforeEach
    void setUp() {
        EncryptionProperties encryptionProperties = new EncryptionProperties(ENCRYPTION_KEY);
        oAuthSupabaseService =
                new OAuthSupabaseService(oAuthStateStore, supabaseManagementClient, onboardingSessionStore, accountRepository, accountService,
                        oAuthSessionService, encryptionProperties);
    }

    private SupabaseTokenResponse tokenResponse() {
        return new SupabaseTokenResponse("raw-access-token", "raw-refresh-token", 3600, "Bearer");
    }

    private SupabaseProject project(String ref) {
        return new SupabaseProject(ref, "Project", "ACTIVE_HEALTHY", "2026-01-01");
    }


    private void mockValidState(UUID accountId) {
        when(oAuthStateStore.validateAndGet("state")).thenReturn(new OAuthStateStore.ValidationResult(true, accountId));
    }

    private void mockTokenExchange() {
        when(supabaseManagementClient.exchangeCodeForTokens("code")).thenReturn(tokenResponse());
        when(supabaseManagementClient.getOwnerEmail("raw-access-token")).thenReturn("owner@example.com");
    }

    // State validation

    @Test
    void shouldThrowWhenStateIsInvalid() {
        when(oAuthStateStore.validateAndGet("state")).thenReturn(new OAuthStateStore.ValidationResult(false, null));

        assertThatThrownBy(() -> oAuthSupabaseService.handleCallback("code", "state")).isInstanceOf(OAuthStateValidationException.class);
    }

    // Returning user

    @Test
    void shouldReturnReturningUserWhenEmailAlreadyExists() {
        mockValidState(null);
        mockTokenExchange();
        Account account = Account.builder().id(UUID.randomUUID()).build();
        when(accountRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(account));
        when(accountService.rotateApiKey(account.getId())).thenReturn("new-api-key");

        OAuthCallbackResult result = oAuthSupabaseService.handleCallback("code", "state");

        assertThat(result).isInstanceOf(OAuthCallbackResult.ReturningUser.class);
        assertThat(((OAuthCallbackResult.ReturningUser) result).apiKey()).isEqualTo("new-api-key");
    }

    // New user

    @Test
    void shouldReturnNewUserSingleProjectWhenOneProject() {
        mockValidState(null);
        mockTokenExchange();
        when(accountRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(supabaseManagementClient.listProjects("raw-access-token")).thenReturn(List.of(project("ref-1")));
        when(oAuthSessionService.setupProjectAndCreateSession(any(), any(), any(), any(), any(), any())).thenReturn(
                new OAuthCallbackResult.NewUserSingleProject("session-token"));

        OAuthCallbackResult result = oAuthSupabaseService.handleCallback("code", "state");

        assertThat(result).isInstanceOf(OAuthCallbackResult.NewUserSingleProject.class);
    }

    @Test
    void shouldReturnNewUserMultipleProjectsWhenManyProjects() {
        mockValidState(null);
        mockTokenExchange();
        when(accountRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(supabaseManagementClient.listProjects("raw-access-token")).thenReturn(List.of(project("ref-1"), project("ref-2")));
        when(onboardingSessionStore.createPartial(any())).thenReturn("partial-token");

        OAuthCallbackResult result = oAuthSupabaseService.handleCallback("code", "state");

        assertThat(result).isInstanceOf(OAuthCallbackResult.NewUserMultipleProjects.class);
        assertThat(((OAuthCallbackResult.NewUserMultipleProjects) result).partialSessionToken()).isEqualTo("partial-token");
    }

    @Test
    void shouldThrowWhenNoProjectsFound() {
        mockValidState(null);
        mockTokenExchange();
        when(accountRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(supabaseManagementClient.listProjects("raw-access-token")).thenReturn(List.of());

        assertThatThrownBy(() -> oAuthSupabaseService.handleCallback("code", "state")).isInstanceOf(NoSupabaseProjectsException.class);
    }

    // Reconfigure

    @Test
    void shouldReturnReconfigureSingleProjectWhenAccountIdInState() {
        mockValidState(ACCOUNT_ID);
        mockTokenExchange();
        when(supabaseManagementClient.listProjects("raw-access-token")).thenReturn(List.of(project("ref-1")));
        when(oAuthSessionService.setupReconfigureSession(any(), any(), any(), any(), any(), any())).thenReturn("session-token");

        OAuthCallbackResult result = oAuthSupabaseService.handleCallback("code", "state");

        assertThat(result).isInstanceOf(OAuthCallbackResult.ReconfigureSingleProject.class);
    }

    @Test
    void shouldReturnReconfigureMultipleProjectsWhenAccountIdInStateAndManyProjects() {
        mockValidState(ACCOUNT_ID);
        mockTokenExchange();
        when(supabaseManagementClient.listProjects("raw-access-token")).thenReturn(List.of(project("ref-1"), project("ref-2")));
        when(onboardingSessionStore.createPartial(any())).thenReturn("partial-token");

        OAuthCallbackResult result = oAuthSupabaseService.handleCallback("code", "state");

        assertThat(result).isInstanceOf(OAuthCallbackResult.ReconfigureMultipleProjects.class);
    }
}
