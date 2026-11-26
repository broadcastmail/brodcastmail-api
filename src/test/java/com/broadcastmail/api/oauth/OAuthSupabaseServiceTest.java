package com.broadcastmail.api.oauth;

import com.broadcastmail.api.auth.SessionService;
import com.broadcastmail.api.common.exceptions.NoSupabaseProjectsException;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.oauth.dto.OAuthCallbackResult;
import com.broadcastmail.api.supabase.management.SupabaseManagementClient;
import com.broadcastmail.api.supabase.management.dto.SupabaseProject;
import com.broadcastmail.api.supabase.management.dto.SupabaseTokenResponse;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
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
    private static final String CODE = "auth-code";
    private static final String VERIFIER = "test-code-verifier";
    private static final String REDIRECT_URI = "http://localhost:3000/api/oauth/callback";
    private static final UUID ACCOUNT_ID = UUID.randomUUID();

    @Mock
    private SupabaseManagementClient supabaseManagementClient;
    @Mock
    private OAuthSessionStore onboardingSessionStore;
    @Mock
    private ConnectionRepository connectionRepository;
    @Mock
    private SessionService sessionService;
    @Mock
    private OAuthSessionService oAuthSessionService;

    private OAuthSupabaseService oAuthSupabaseService;

    @BeforeEach
    void setUp() {
        EncryptionProperties encryptionProperties = new EncryptionProperties(ENCRYPTION_KEY);
        oAuthSupabaseService = new OAuthSupabaseService(
                supabaseManagementClient, onboardingSessionStore, connectionRepository,
                sessionService, oAuthSessionService, encryptionProperties);
    }

    private SupabaseTokenResponse tokenResponse() {
        return new SupabaseTokenResponse("raw-access-token", "raw-refresh-token", 3600, "Bearer");
    }

    private SupabaseProject project(String ref) {
        return new SupabaseProject(ref, "Project", "ACTIVE_HEALTHY", "2026-01-01", "us-east-1", "aws");
    }

    private void mockTokenExchange() {
        when(supabaseManagementClient.exchangeCodeForTokensPKCE(CODE, VERIFIER, REDIRECT_URI)).thenReturn(tokenResponse());
    }

    // Returning user

    @Test
    void shouldReturnReturningUserWhenProjectRefAlreadyConnected() {
        mockTokenExchange();
        UUID existingAccountId = UUID.randomUUID();
        Connection connection = Connection.builder().accountId(existingAccountId).build();
        when(supabaseManagementClient.listProjects("raw-access-token")).thenReturn(List.of(project("ref-1")));
        when(connectionRepository.findByProjectRef("ref-1")).thenReturn(Optional.of(connection));
        when(sessionService.create(eq(existingAccountId), any())).thenReturn("session-token");

        OAuthCallbackResult result = oAuthSupabaseService.exchangeWithPKCE(CODE, VERIFIER, REDIRECT_URI, null, null);

        assertThat(result).isInstanceOf(OAuthCallbackResult.ReturningUser.class);
        assertThat(((OAuthCallbackResult.ReturningUser) result).sessionToken()).isEqualTo("session-token");
    }

    // New user

    @Test
    void shouldReturnNewUserSingleProjectWhenOneProject() {
        mockTokenExchange();
        when(supabaseManagementClient.listProjects("raw-access-token")).thenReturn(List.of(project("ref-1")));
        when(connectionRepository.findByProjectRef("ref-1")).thenReturn(Optional.empty());
        when(oAuthSessionService.setupProjectAndCreateSession(any(), any(), any(), any(), any(), any())).thenReturn(
                new OAuthCallbackResult.NewUserSingleProject("session-token"));

        OAuthCallbackResult result = oAuthSupabaseService.exchangeWithPKCE(CODE, VERIFIER, REDIRECT_URI, null, null);

        assertThat(result).isInstanceOf(OAuthCallbackResult.NewUserSingleProject.class);
    }

    @Test
    void shouldReturnNewUserMultipleProjectsWhenManyProjects() {
        mockTokenExchange();
        when(supabaseManagementClient.listProjects("raw-access-token")).thenReturn(List.of(project("ref-1"), project("ref-2")));
        when(connectionRepository.findByProjectRef(any())).thenReturn(Optional.empty());
        when(onboardingSessionStore.createPartial(any())).thenReturn("partial-token");

        OAuthCallbackResult result = oAuthSupabaseService.exchangeWithPKCE(CODE, VERIFIER, REDIRECT_URI, null, null);

        assertThat(result).isInstanceOf(OAuthCallbackResult.NewUserMultipleProjects.class);
        assertThat(((OAuthCallbackResult.NewUserMultipleProjects) result).partialSessionToken()).isEqualTo("partial-token");
    }

    @Test
    void shouldThrowWhenNoProjectsFound() {
        mockTokenExchange();
        when(supabaseManagementClient.listProjects("raw-access-token")).thenReturn(List.of());

        assertThatThrownBy(() -> oAuthSupabaseService.exchangeWithPKCE(CODE, VERIFIER, REDIRECT_URI, null, null))
                .isInstanceOf(NoSupabaseProjectsException.class);
    }

    // Reconfigure

    @Test
    void shouldReturnReconfigureSingleProjectWhenAccountIdProvided() {
        mockTokenExchange();
        when(supabaseManagementClient.listProjects("raw-access-token")).thenReturn(List.of(project("ref-1")));
        when(oAuthSessionService.setupReconfigureSession(any(), any(), any(), any(), any(), any(), any())).thenReturn("session-token");

        OAuthCallbackResult result = oAuthSupabaseService.exchangeWithPKCE(CODE, VERIFIER, REDIRECT_URI, ACCOUNT_ID, null);

        assertThat(result).isInstanceOf(OAuthCallbackResult.ReconfigureSingleProject.class);
    }

    @Test
    void shouldReturnReconfigureMultipleProjectsWhenAccountIdProvidedAndManyProjects() {
        mockTokenExchange();
        when(supabaseManagementClient.listProjects("raw-access-token")).thenReturn(List.of(project("ref-1"), project("ref-2")));
        when(onboardingSessionStore.createPartial(any())).thenReturn("partial-token");

        OAuthCallbackResult result = oAuthSupabaseService.exchangeWithPKCE(CODE, VERIFIER, REDIRECT_URI, ACCOUNT_ID, null);

        assertThat(result).isInstanceOf(OAuthCallbackResult.ReconfigureMultipleProjects.class);
    }
}
