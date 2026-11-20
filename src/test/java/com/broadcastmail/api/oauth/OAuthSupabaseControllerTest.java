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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestContainersConfiguration.class)
class OAuthSupabaseControllerTest extends IntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    private final ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

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
    private static final String REDIRECT_URI = "http://localhost:3000/api/oauth/callback";

    private MockMvcTester.MockMvcRequestBuilder callbackRequest(String code) {
        return callbackRequest(code, STATE, VERIFIER, null);
    }

    private MockMvcTester.MockMvcRequestBuilder callbackRequest(
            String code, String state, String verifier, String bmSession) {
        String bmSessionJson = bmSession != null ? "\"" + bmSession + "\"" : "null";
        String body = """
                {"code":"%s","state":"%s","storedState":"%s","codeVerifier":"%s","redirectUri":"%s","bmSession":%s}
                """.formatted(code, state, state, verifier, REDIRECT_URI, bmSessionJson).strip();
        return mockMvc.post()
                .uri("/api/v1/oauth/supabase/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    @Test
    void shouldRejectCallbackWithMissingStoredState() {
        String body = """
                {"code":"somecode","state":"%s","storedState":null,"codeVerifier":"%s","bmSession":null}
                """.formatted(STATE, VERIFIER).strip();

        var result = mockMvc.post()
                .uri("/api/v1/oauth/supabase/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();

        assertThat(result).hasStatus(401);
    }

    @Test
    void shouldRejectCallbackWithStateMismatch() {
        String body = """
                {"code":"somecode","state":"wrong-state","storedState":"%s","codeVerifier":"%s","bmSession":null}
                """.formatted(STATE, VERIFIER).strip();

        var result = mockMvc.post()
                .uri("/api/v1/oauth/supabase/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();

        assertThat(result).hasStatus(401);
    }

    @Test
    void shouldReturnOnboardingTokenAfterSuccessfulOAuthWithSingleProject() {
        when(supabaseManagementClient.exchangeCodeForTokensPKCE(eq("valid-code"), eq(VERIFIER), anyString()))
                .thenReturn(new SupabaseTokenResponse("access-token", "refresh-token", 3600, "Bearer"));
        when(supabaseManagementClient.listProjects("access-token"))
                .thenReturn(List.of(new SupabaseProject("project-ref", "My Project", "ACTIVE_HEALTHY", "2026-01-01", "us-east-1", "aws")));
        doNothing().when(supabaseManagementClient).executeSql(anyString(), anyString(), anyString());

        var result = callbackRequest("valid-code").exchange();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.type").asString().isEqualTo("NEW_USER");
        assertThat(result).bodyJson().extractingPath("$.onboardingToken").asString().isNotBlank();
    }

    @Test
    void shouldReturnPartialTokenWhenMultipleProjects() {
        when(supabaseManagementClient.exchangeCodeForTokensPKCE(eq("valid-code"), eq(VERIFIER), anyString()))
                .thenReturn(new SupabaseTokenResponse("access-token", "refresh-token", 3600, "Bearer"));
        when(supabaseManagementClient.listProjects("access-token"))
                .thenReturn(List.of(
                        new SupabaseProject("ref-1", "Project 1", "ACTIVE_HEALTHY", "2026-01-01", "us-east-1", "aws"),
                        new SupabaseProject("ref-2", "Project 2", "ACTIVE_HEALTHY", "2026-01-01", "us-east-1", "aws")
                ));

        var result = callbackRequest("valid-code").exchange();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.type").asString().isEqualTo("MULTIPLE_PROJECTS");
        assertThat(result).bodyJson().extractingPath("$.partialToken").asString().isNotBlank();
    }

    @Test
    void shouldListProjectsForPartialSession() throws Exception {
        when(supabaseManagementClient.exchangeCodeForTokensPKCE(eq("valid-code"), eq(VERIFIER), anyString()))
                .thenReturn(new SupabaseTokenResponse("access-token", "refresh-token", 3600, "Bearer"));
        when(supabaseManagementClient.listProjects("access-token"))
                .thenReturn(List.of(
                        new SupabaseProject("ref-1", "Project 1", "ACTIVE_HEALTHY", "2026-01-01", "us-east-1", "aws"),
                        new SupabaseProject("ref-2", "Project 2", "PAUSED", "2026-01-01", "us-east-1", "aws")
                ));

        var callback = callbackRequest("valid-code").exchange();
        String partialToken = objectMapper.readTree(callback.getResponse().getContentAsByteArray()).get("partialToken").asText();

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
        when(supabaseManagementClient.exchangeCodeForTokensPKCE(eq("valid-code"), eq(VERIFIER), anyString()))
                .thenReturn(new SupabaseTokenResponse("access-token", "refresh-token", 3600, "Bearer"));
        when(supabaseManagementClient.listProjects("access-token")).thenReturn(List.of());

        var result = callbackRequest("valid-code").exchange();

        assertThat(result).hasStatus(422);
    }

    @Test
    void shouldReturnSessionTokenForReturningUser() {
        Account account = accountRepository.save(CampaignTestFixtures.account().build());
        connectionRepository.save(CampaignTestFixtures.connection(account.getId()).projectRef("project-ref").build());

        when(supabaseManagementClient.exchangeCodeForTokensPKCE(eq("valid-code"), eq(VERIFIER), anyString()))
                .thenReturn(new SupabaseTokenResponse("access-token", "refresh-token", 3600, "Bearer"));
        when(supabaseManagementClient.listProjects("access-token"))
                .thenReturn(List.of(new SupabaseProject("project-ref", "My Project", "ACTIVE_HEALTHY", "2026-01-01", "us-east-1", "aws")));

        var result = callbackRequest("valid-code").exchange();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.type").asString().isEqualTo("RETURNING_USER");
        assertThat(result).bodyJson().extractingPath("$.sessionToken").asString().isNotBlank();
    }

    @Test
    void shouldReturnOnboardingTokenForSingleProjectReconfigure() {
        Account account = accountRepository.save(CampaignTestFixtures.account()
                .email("returning-user@example.com").build());
        connectionRepository.save(CampaignTestFixtures.connection(account.getId()).build());

        String reconfigureState = "abc123:reconfigure";
        when(supabaseManagementClient.exchangeCodeForTokensPKCE(eq("valid-code"), eq(VERIFIER), anyString()))
                .thenReturn(new SupabaseTokenResponse("access-token", "refresh-token", 3600, "Bearer"));
        when(supabaseManagementClient.listProjects("access-token"))
                .thenReturn(List.of(new SupabaseProject("ref-1", "Project", "ACTIVE_HEALTHY", "2026-01-01", "us-east-1", "aws")));
        doNothing().when(supabaseManagementClient).executeSql(anyString(), anyString(), anyString());

        var result = callbackRequest("valid-code", reconfigureState, VERIFIER, TEST_API_KEY).exchange();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.type").asString().isEqualTo("RECONFIGURE");
        assertThat(result).bodyJson().extractingPath("$.onboardingToken").asString().isNotBlank();
    }

    @Test
    void shouldReturnPartialTokenForReconfigureWithMultipleProjects() {
        Account account = accountRepository.save(CampaignTestFixtures.account()
                .email("returning-user@example.com").build());
        connectionRepository.save(CampaignTestFixtures.connection(account.getId()).build());

        String reconfigureState = "abc123:reconfigure";
        when(supabaseManagementClient.exchangeCodeForTokensPKCE(eq("valid-code"), eq(VERIFIER), anyString()))
                .thenReturn(new SupabaseTokenResponse("access-token", "refresh-token", 3600, "Bearer"));
        when(supabaseManagementClient.listProjects("access-token"))
                .thenReturn(List.of(
                        new SupabaseProject("ref-1", "Project 1", "ACTIVE_HEALTHY", "2026-01-01", "us-east-1", "aws"),
                        new SupabaseProject("ref-2", "Project 2", "ACTIVE_HEALTHY", "2026-01-01", "us-east-1", "aws")
                ));

        var result = callbackRequest("valid-code", reconfigureState, VERIFIER, TEST_API_KEY).exchange();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.type").asString().isEqualTo("RECONFIGURE_MULTIPLE");
        assertThat(result).bodyJson().extractingPath("$.partialToken").asString().isNotBlank();
    }
}
