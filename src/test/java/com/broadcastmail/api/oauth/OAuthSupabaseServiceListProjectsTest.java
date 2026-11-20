package com.broadcastmail.api.oauth;

import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.oauth.dto.ProjectOption;
import com.broadcastmail.api.onboarding.PartialOnboardingSession;
import com.broadcastmail.api.supabase.SupabaseManagementClient;
import com.broadcastmail.api.supabase.dto.SupabaseProject;
import com.broadcastmail.common.connection.ConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthSupabaseServiceListProjectsTest {

    private static final String ENCRYPTION_KEY = "test-encryption-key-32-chars-okk";

    @Mock private OAuthSessionStore onboardingSessionStore;
    @Mock private SupabaseManagementClient supabaseManagementClient;
    @Mock private EncryptionProperties encryptionProperties;
    @Mock private ConnectionRepository connectionRepository;

    private OAuthSessionService oAuthSessionService;

    @BeforeEach
    void setUp() {
        oAuthSessionService = new OAuthSessionService(onboardingSessionStore, supabaseManagementClient, encryptionProperties, connectionRepository);
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
    void shouldReturnProjectsWithNullUserCount() {
        List<SupabaseProject> projects = List.of(
                new SupabaseProject("ref-1", "Project 1", "ACTIVE_HEALTHY", "2026-01-01", "us-east-1", "aws"),
                new SupabaseProject("ref-2", "Project 2", "PAUSED", "2026-01-01", "us-east-1", "aws")
        );
        when(onboardingSessionStore.getPartial("partial-token"))
                .thenReturn(partialSessionWith(projects));

        List<ProjectOption> options = oAuthSessionService.listPartialProjects("partial-token");

        assertThat(options).containsExactly(
                new ProjectOption("ref-1", "Project 1", "ACTIVE_HEALTHY"),
                new ProjectOption("ref-2", "Project 2", "PAUSED")
        );
    }
}
