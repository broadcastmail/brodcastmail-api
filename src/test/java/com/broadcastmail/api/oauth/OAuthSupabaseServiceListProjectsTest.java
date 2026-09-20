package com.broadcastmail.api.oauth;

import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.oauth.dto.ProjectOption;
import com.broadcastmail.api.onboarding.PartialOnboardingSession;
import com.broadcastmail.api.supabase.SupabaseManagementClient;
import com.broadcastmail.api.supabase.SupabaseSql;
import com.broadcastmail.api.supabase.dto.SupabaseProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthSupabaseServiceListProjectsTest {

    private static final String ENCRYPTION_KEY = "test-encryption-key-32-chars-okk";

    @Mock
    private OAuthStateStore oAuthStateStore;
    @Mock private SupabaseManagementClient supabaseManagementClient;
    @Mock private OAuthSessionStore onboardingSessionStore;

    @InjectMocks
    private OAuthSessionService oAuthSessionService;

    @BeforeEach
    void setUp() {
        oAuthSessionService = new OAuthSessionService(
                supabaseManagementClient,
                onboardingSessionStore,
                new EncryptionProperties(ENCRYPTION_KEY)
        );
    }

    private PartialOnboardingSession partialSessionWith(List<SupabaseProject> projects) {
        return PartialOnboardingSession.forNewUser(
                "owner@example.com",
                SecurityUtil.encrypt("raw-access-token", ENCRYPTION_KEY),
                SecurityUtil.encrypt("raw-refresh-token", ENCRYPTION_KEY),
                Instant.now().plusSeconds(3600),
                projects
        );
    }

    @Test
    void shouldReturnUserCountForActiveProject() {
        SupabaseProject project = new SupabaseProject("ref-1", "Project 1", "ACTIVE_HEALTHY", "2026-01-01");
        when(onboardingSessionStore.getPartial("partial-token"))
                .thenReturn(partialSessionWith(List.of(project)));
        doReturn(List.of(Map.of("count", "5")))
                .when(supabaseManagementClient)
                .executeSqlQuery("raw-access-token", "ref-1", SupabaseSql.COUNT_AUTH_USERS);

        List<ProjectOption> options = oAuthSessionService.listPartialProjects("partial-token");

        assertThat(options).containsExactly(new ProjectOption("ref-1", "Project 1", "ACTIVE_HEALTHY", 5));
    }

    @Test
    void shouldSkipCountQueryForNonActiveProject() {
        SupabaseProject project = new SupabaseProject("ref-1", "Project 1", "PAUSED", "2026-01-01");
        when(onboardingSessionStore.getPartial("partial-token"))
                .thenReturn(partialSessionWith(List.of(project)));

        List<ProjectOption> options = oAuthSessionService.listPartialProjects("partial-token");

        assertThat(options).containsExactly(new ProjectOption("ref-1", "Project 1", "PAUSED", null));
        verify(supabaseManagementClient, never()).executeSqlQuery(anyString(), anyString(), anyString());
    }

    @Test
    void shouldReturnNullUserCountWhenQueryFails() {
        SupabaseProject project = new SupabaseProject("ref-1", "Project 1", "ACTIVE_HEALTHY", "2026-01-01");
        when(onboardingSessionStore.getPartial("partial-token"))
                .thenReturn(partialSessionWith(List.of(project)));
        when(supabaseManagementClient.executeSqlQuery(anyString(), anyString(), anyString()))
                .thenThrow(new RestClientException("boom"));

        List<ProjectOption> options = oAuthSessionService.listPartialProjects("partial-token");

        assertThat(options).containsExactly(new ProjectOption("ref-1", "Project 1", "ACTIVE_HEALTHY", null));
    }

    @Test
    void shouldNotLetOneFailingProjectBreakTheRestOfTheList() {
        SupabaseProject healthy = new SupabaseProject("ref-1", "Project 1", "ACTIVE_HEALTHY", "2026-01-01");
        SupabaseProject failing = new SupabaseProject("ref-2", "Project 2", "ACTIVE_HEALTHY", "2026-01-01");
        when(onboardingSessionStore.getPartial("partial-token"))
                .thenReturn(partialSessionWith(List.of(healthy, failing)));
        when(supabaseManagementClient.executeSqlQuery(eq("raw-access-token"), eq("ref-1"), anyString()))
                .thenReturn(List.of(Map.of("count", "12")));
        when(supabaseManagementClient.executeSqlQuery(eq("raw-access-token"), eq("ref-2"), anyString()))
                .thenThrow(new RestClientException("boom"));

        List<ProjectOption> options = oAuthSessionService.listPartialProjects("partial-token");

        assertThat(options).containsExactly(
                new ProjectOption("ref-1", "Project 1", "ACTIVE_HEALTHY", 12),
                new ProjectOption("ref-2", "Project 2", "ACTIVE_HEALTHY", null)
        );
    }
}
