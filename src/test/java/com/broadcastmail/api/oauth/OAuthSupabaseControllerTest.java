package com.broadcastmail.api.oauth;

import com.broadcastmail.api.IntegrationTest;
import com.broadcastmail.api.TestContainersConfiguration;
import com.broadcastmail.api.support.CampaignTestFixtures;
import static com.broadcastmail.api.support.CampaignTestFixtures.TEST_API_KEY;
import com.broadcastmail.api.supabase.SupabaseManagementClient;
import com.broadcastmail.api.supabase.dto.SupabaseProject;
import com.broadcastmail.api.supabase.dto.SupabaseTokenResponse;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import com.broadcastmail.common.connection.ConnectionRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
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
class OAuthSupabaseControllerTest extends IntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ConnectionRepository connectionRepository;

    @MockitoBean
    private SupabaseManagementClient supabaseManagementClient;

    @AfterEach
    void tearDown() {
        accountRepository.deleteAll();
    }

    private static final String STATE = "abc123:new_user";
    private static final String VERIFIER = "test-code-verifier";

    private MockMvcTester.MockMvcRequestBuilder callbackRequest(String code) {
        return mockMvc.get()
                .uri("/api/v1/oauth/supabase/callback")
                .param("code", code)
                .param("state", STATE)
                .cookie(new Cookie("oauth_state", STATE),
                        new Cookie("oauth_code_verifier", VERIFIER));
    }

    @Test
    void shouldRejectCallbackWithMissingStateCookie() {
        var result = mockMvc.get()
                .uri("/api/v1/oauth/supabase/callback")
                .param("code", "somecode")
                .param("state", STATE)
                .exchange();

        assertThat(result).hasStatus(401);
    }

    @Test
    void shouldRejectCallbackWithStateMismatch() {
        var result = mockMvc.get()
                .uri("/api/v1/oauth/supabase/callback")
                .param("code", "somecode")
                .param("state", "wrong-state")
                .cookie(new Cookie("oauth_state", STATE),
                        new Cookie("oauth_code_verifier", VERIFIER))
                .exchange();

        assertThat(result).hasStatus(401);
    }

    @Test
    void shouldSetSessionCookieAfterSuccessfulOAuthWithSingleProject() {
        when(supabaseManagementClient.exchangeCodeForTokensPKCE("valid-code", VERIFIER))
                .thenReturn(new SupabaseTokenResponse("access-token", "refresh-token", 3600, "Bearer"));
        when(supabaseManagementClient.getOwnerEmail("access-token")).thenReturn("owner@example.com");
        when(supabaseManagementClient.listProjects("access-token"))
                .thenReturn(List.of(new SupabaseProject("project-ref", "My Project", "ACTIVE_HEALTHY", "2026-01-01")));
        doNothing().when(supabaseManagementClient).executeSql(anyString(), anyString(), anyString());

        var result = callbackRequest("valid-code").exchange();

        assertThat(result).hasStatus(302);
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("onboarding_session");
    }

    @Test
    void shouldRedirectToProjectPickerWhenMultipleProjects() {
        when(supabaseManagementClient.exchangeCodeForTokensPKCE("valid-code", VERIFIER))
                .thenReturn(new SupabaseTokenResponse("access-token", "refresh-token", 3600, "Bearer"));
        when(supabaseManagementClient.getOwnerEmail("access-token")).thenReturn("owner@example.com");
        when(supabaseManagementClient.listProjects("access-token"))
                .thenReturn(List.of(
                        new SupabaseProject("ref-1", "Project 1", "ACTIVE_HEALTHY", "2026-01-01"),
                        new SupabaseProject("ref-2", "Project 2", "ACTIVE_HEALTHY", "2026-01-01")
                ));

        var result = callbackRequest("valid-code").exchange();

        assertThat(result).hasStatus(302);
        assertThat(result.getResponse().getHeader("Location")).contains("select-project");
        assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
    }

    @Test
    void shouldListProjectsForPartialSession() {
        when(supabaseManagementClient.exchangeCodeForTokensPKCE("valid-code", VERIFIER))
                .thenReturn(new SupabaseTokenResponse("access-token", "refresh-token", 3600, "Bearer"));
        when(supabaseManagementClient.getOwnerEmail("access-token")).thenReturn("owner@example.com");
        when(supabaseManagementClient.listProjects("access-token"))
                .thenReturn(List.of(
                        new SupabaseProject("ref-1", "Project 1", "ACTIVE_HEALTHY", "2026-01-01"),
                        new SupabaseProject("ref-2", "Project 2", "PAUSED", "2026-01-01")
                ));

        var callback = callbackRequest("valid-code").exchange();
        String location = callback.getResponse().getHeader("Location");
        String partialToken = location.substring(location.indexOf("partialToken=") + "partialToken=".length());

        var result = mockMvc.get()
                .uri("/api/v1/oauth/supabase/projects")
                .param("partialToken", partialToken)
                .exchange();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$[0].ref").asString().isEqualTo("ref-1");
        assertThat(result).bodyJson().extractingPath("$[1].ref").asString().isEqualTo("ref-2");
    }

    @Test
    void shouldRejectListProjectsWithInvalidPartialToken() {
        var result = mockMvc.get()
                .uri("/api/v1/oauth/supabase/projects")
                .param("partialToken", "not-a-real-token")
                .exchange();

        assertThat(result).hasStatus(401);
    }

    @Test
    void shouldRejectCallbackWithNoProjects() {
        when(supabaseManagementClient.exchangeCodeForTokensPKCE("valid-code", VERIFIER))
                .thenReturn(new SupabaseTokenResponse("access-token", "refresh-token", 3600, "Bearer"));
        when(supabaseManagementClient.getOwnerEmail("access-token")).thenReturn("owner@example.com");
        when(supabaseManagementClient.listProjects("access-token")).thenReturn(List.of());

        var result = callbackRequest("valid-code").exchange();

        assertThat(result).hasStatus(422);
    }

    @Test
    void shouldSetSessionCookieAndRedirectToDashboardForReturningUser() {
        accountRepository.save(CampaignTestFixtures.account().email("returning-user@example.com").build());

        when(supabaseManagementClient.exchangeCodeForTokensPKCE("valid-code", VERIFIER))
                .thenReturn(new SupabaseTokenResponse("access-token", "refresh-token", 3600, "Bearer"));
        when(supabaseManagementClient.getOwnerEmail("access-token")).thenReturn("returning-user@example.com");

        var result = callbackRequest("valid-code").exchange();

        assertThat(result).hasStatus(302);
        assertThat(result.getResponse().getHeader("Location")).contains("/dashboard");
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("bm_session");
    }

    @Test
    void shouldSetOnboardingCookieAndRedirectToReconfigureSchemaForSingleProjectReconfigure() {
        Account account = accountRepository.save(CampaignTestFixtures.account()
                .email("returning-user@example.com").build());
        connectionRepository.save(CampaignTestFixtures.connection(account.getId()).build());

        String reconfigureState = "abc123:reconfigure";
        when(supabaseManagementClient.exchangeCodeForTokensPKCE("valid-code", VERIFIER))
                .thenReturn(new SupabaseTokenResponse("access-token", "refresh-token", 3600, "Bearer"));
        when(supabaseManagementClient.getOwnerEmail("access-token")).thenReturn("returning-user@example.com");
        when(supabaseManagementClient.listProjects("access-token"))
                .thenReturn(List.of(new SupabaseProject("ref-1", "Project", "ACTIVE_HEALTHY", "2026-01-01")));
        doNothing().when(supabaseManagementClient).executeSql(anyString(), anyString(), anyString());

        var result = mockMvc.get()
                .uri("/api/v1/oauth/supabase/callback")
                .param("code", "valid-code")
                .param("state", reconfigureState)
                .cookie(new Cookie("oauth_state", reconfigureState),
                        new Cookie("oauth_code_verifier", VERIFIER),
                        new Cookie("bm_session", TEST_API_KEY))
                .exchange();

        assertThat(result).hasStatus(302);
        assertThat(result.getResponse().getHeader("Location")).contains("/settings/reconfigure/schema");
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("onboarding_session");
    }

    @Test
    void shouldRedirectToReconfigureProjectPickerWhenMultipleProjectsOnReconfigure() {
        Account account = accountRepository.save(CampaignTestFixtures.account()
                .email("returning-user@example.com").build());
        connectionRepository.save(CampaignTestFixtures.connection(account.getId()).build());

        String reconfigureState = "abc123:reconfigure";
        when(supabaseManagementClient.exchangeCodeForTokensPKCE("valid-code", VERIFIER))
                .thenReturn(new SupabaseTokenResponse("access-token", "refresh-token", 3600, "Bearer"));
        when(supabaseManagementClient.getOwnerEmail("access-token")).thenReturn("returning-user@example.com");
        when(supabaseManagementClient.listProjects("access-token"))
                .thenReturn(List.of(
                        new SupabaseProject("ref-1", "Project 1", "ACTIVE_HEALTHY", "2026-01-01"),
                        new SupabaseProject("ref-2", "Project 2", "ACTIVE_HEALTHY", "2026-01-01")
                ));

        var result = mockMvc.get()
                .uri("/api/v1/oauth/supabase/callback")
                .param("code", "valid-code")
                .param("state", reconfigureState)
                .cookie(new Cookie("oauth_state", reconfigureState),
                        new Cookie("oauth_code_verifier", VERIFIER),
                        new Cookie("bm_session", TEST_API_KEY))
                .exchange();

        assertThat(result).hasStatus(302);
        assertThat(result.getResponse().getHeader("Location")).contains("/settings/reconfigure/select-project");
        assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
    }
}
