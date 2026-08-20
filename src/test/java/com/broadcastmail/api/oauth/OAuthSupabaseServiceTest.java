package com.broadcastmail.api.oauth;

import com.broadcastmail.api.account.AccountService;
import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.oauth.dto.ProjectOption;
import com.broadcastmail.api.onboarding.OnboardingSessionStore;
import com.broadcastmail.api.onboarding.PartialOnboardingSession;
import com.broadcastmail.api.supabase.SupabaseManagementClient;
import com.broadcastmail.api.supabase.SupabaseSql;
import com.broadcastmail.api.supabase.dto.SupabaseProject;
import com.broadcastmail.common.account.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthSupabaseServiceTest {

    private static final String ENCRYPTION_KEY = "test-encryption-key-32-chars-okk"; // SecurityUtil requires exactly 32 chars

    @Mock
    private OAuthStateStore oAuthStateStore;

    @Mock
    private SupabaseManagementClient supabaseManagementClient;

    @Mock
    private OnboardingSessionStore onboardingSessionStore;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountService accountService;

    private OAuthSupabaseService oAuthSupabaseService;

    @BeforeEach
    void setUp() {
        EncryptionProperties encryptionProperties = new EncryptionProperties(ENCRYPTION_KEY);
        oAuthSupabaseService = new OAuthSupabaseService(
                oAuthStateStore,
                supabaseManagementClient,
                onboardingSessionStore,
                accountRepository,
                accountService,
                encryptionProperties
        );
    }

    private PartialOnboardingSession partialSessionWith(List<SupabaseProject> projects) {
        return new PartialOnboardingSession(
                "owner@example.com",
                SecurityUtil.encrypt("raw-access-token", ENCRYPTION_KEY),
                SecurityUtil.encrypt("raw-refresh-token", ENCRYPTION_KEY),
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(1800),
                projects
        );
    }

    @Test
    void shouldReturnUserCountForActiveProject() {
        // Given
        SupabaseProject project = new SupabaseProject("ref-1", "Project 1", "ACTIVE_HEALTHY", "2026-01-01");
        when(onboardingSessionStore.getPartial("partial-token"))
                .thenReturn(partialSessionWith(List.of(project)));
        doReturn(List.of(Map.of("count", "5")))
                .when(supabaseManagementClient)
                .executeSqlQuery("raw-access-token", "ref-1", SupabaseSql.COUNT_AUTH_USERS);

        // When
        List<ProjectOption> options = oAuthSupabaseService.listPartialProjects("partial-token");

        // Then
        assertThat(options).containsExactly(new ProjectOption("ref-1", "Project 1", "ACTIVE_HEALTHY", 5));
    }

    @Test
    void shouldSkipCountQueryForNonActiveProject() {
        // Given
        SupabaseProject project = new SupabaseProject("ref-1", "Project 1", "PAUSED", "2026-01-01");
        when(onboardingSessionStore.getPartial("partial-token"))
                .thenReturn(partialSessionWith(List.of(project)));

        // When
        List<ProjectOption> options = oAuthSupabaseService.listPartialProjects("partial-token");

        // Then
        assertThat(options).containsExactly(new ProjectOption("ref-1", "Project 1", "PAUSED", null));
        verify(supabaseManagementClient, never()).executeSqlQuery(anyString(), anyString(), anyString());
    }

    @Test
    void shouldReturnNullUserCountWhenQueryFails() {
        // Given
        SupabaseProject project = new SupabaseProject("ref-1", "Project 1", "ACTIVE_HEALTHY", "2026-01-01");
        when(onboardingSessionStore.getPartial("partial-token"))
                .thenReturn(partialSessionWith(List.of(project)));
        when(supabaseManagementClient.executeSqlQuery(anyString(), anyString(), anyString()))
                .thenThrow(new RestClientException("boom"));

        // When
        List<ProjectOption> options = oAuthSupabaseService.listPartialProjects("partial-token");

        // Then
        assertThat(options).containsExactly(new ProjectOption("ref-1", "Project 1", "ACTIVE_HEALTHY", null));
    }

    @Test
    void shouldNotLetOneFailingProjectBreakTheRestOfTheList() {
        // Given
        SupabaseProject healthy = new SupabaseProject("ref-1", "Project 1", "ACTIVE_HEALTHY", "2026-01-01");
        SupabaseProject failing = new SupabaseProject("ref-2", "Project 2", "ACTIVE_HEALTHY", "2026-01-01");
        when(onboardingSessionStore.getPartial("partial-token"))
                .thenReturn(partialSessionWith(List.of(healthy, failing)));
        when(supabaseManagementClient.executeSqlQuery(eq("raw-access-token"), eq("ref-1"), anyString()))
                .thenReturn(List.of(Map.of("count", "12")));
        when(supabaseManagementClient.executeSqlQuery(eq("raw-access-token"), eq("ref-2"), anyString()))
                .thenThrow(new RestClientException("boom"));

        // When
        List<ProjectOption> options = oAuthSupabaseService.listPartialProjects("partial-token");

        // Then
        assertThat(options).containsExactly(
                new ProjectOption("ref-1", "Project 1", "ACTIVE_HEALTHY", 12),
                new ProjectOption("ref-2", "Project 2", "ACTIVE_HEALTHY", null)
        );
    }
}
