package com.broadcastmail.api.oauth;

import com.broadcastmail.api.TestContainersConfiguration;
import com.broadcastmail.api.supabase.SupabaseManagementClient;
import com.broadcastmail.api.supabase.dto.SupabaseProject;
import com.broadcastmail.api.supabase.dto.SupabaseTokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestContainersConfiguration.class)
class OAuthSupabaseControllerTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private OAuthStateStore oAuthStateStore;

    @MockitoBean
    private SupabaseManagementClient supabaseManagementClient;

    @Test
    void shouldRejectCallbackWithInvalidStateParam() {
        // Given
        String fakeState = "thisisaninvalidstateparam";

        // When
        var result = mockMvc.get()
                .uri("/api/v1/oauth/supabase/callback")
                .param("code", "somecode")
                .param("state", fakeState)
                .exchange();

        // Then
        assertThat(result).hasStatus(401);
    }

    @Test
    void shouldRejectCallbackWithExpiredStateParam() {
        // Given
        String state = oAuthStateStore.generateAndStore();
        oAuthStateStore.validate(state);

        // When
        var result = mockMvc.get()
                .uri("/api/v1/oauth/supabase/callback")
                .param("code", "somecode")
                .param("state", state)
                .exchange();

        // Then
        assertThat(result).hasStatus(401);
    }

    @Test
    void shouldSetSessionCookieAfterSuccessfulOAuthWithSingleProject() {
        // Given
        String state = oAuthStateStore.generateAndStore();

        when(supabaseManagementClient.exchangeCodeForTokens("valid-code"))
                .thenReturn(new SupabaseTokenResponse(
                        "access-token",
                        "refresh-token",
                        3600,
                        "Bearer"
                ));
        when(supabaseManagementClient.getOwnerEmail("access-token"))
                .thenReturn("owner@example.com");
        when(supabaseManagementClient.listProjects("access-token"))
                .thenReturn(List.of(new SupabaseProject(
                        "project-ref",
                        "My Project",
                        "ACTIVE_HEALTHY",
                        "2026-01-01"
                )));
        doNothing().when(supabaseManagementClient)
                .executeSql(anyString(), anyString(), anyString());

        // When
        var result = mockMvc.get()
                .uri("/api/v1/oauth/supabase/callback")
                .param("code", "valid-code")
                .param("state", state)
                .exchange();

        // Then
        assertThat(result).hasStatus(302);
        assertThat(result.getResponse().getHeader("Set-Cookie"))
                .contains("onboarding_session");
    }

    @Test
    void shouldRedirectToProjectPickerWhenMultipleProjects() {
        // Given
        String state = oAuthStateStore.generateAndStore();

        when(supabaseManagementClient.exchangeCodeForTokens("valid-code"))
                .thenReturn(new SupabaseTokenResponse(
                        "access-token",
                        "refresh-token",
                        3600,
                        "Bearer"
                ));
        when(supabaseManagementClient.getOwnerEmail("access-token"))
                .thenReturn("owner@example.com");
        when(supabaseManagementClient.listProjects("access-token"))
                .thenReturn(List.of(
                        new SupabaseProject("ref-1", "Project 1", "ACTIVE_HEALTHY", "2026-01-01"),
                        new SupabaseProject("ref-2", "Project 2", "ACTIVE_HEALTHY", "2026-01-01")
                ));

        // When
        var result = mockMvc.get()
                .uri("/api/v1/oauth/supabase/callback")
                .param("code", "valid-code")
                .param("state", state)
                .exchange();

        // Then
        assertThat(result).hasStatus(302);
        assertThat(result.getResponse().getHeader("Location"))
                .contains("select-project");
        assertThat(result.getResponse().getHeader("Set-Cookie"))
                .isNull();
    }

    @Test
    void shouldRejectCallbackWithNoProjects() {
        // Given
        String state = oAuthStateStore.generateAndStore();

        when(supabaseManagementClient.exchangeCodeForTokens("valid-code"))
                .thenReturn(new SupabaseTokenResponse(
                        "access-token",
                        "refresh-token",
                        3600,
                        "Bearer"
                ));
        when(supabaseManagementClient.getOwnerEmail("access-token"))
                .thenReturn("owner@example.com");
        when(supabaseManagementClient.listProjects("access-token"))
                .thenReturn(List.of());

        // When
        var result = mockMvc.get()
                .uri("/api/v1/oauth/supabase/callback")
                .param("code", "valid-code")
                .param("state", state)
                .exchange();

        // Then
        assertThat(result).hasStatus(422);
    }
}