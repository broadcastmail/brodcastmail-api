package com.broadcastmail.api.me;

import com.broadcastmail.api.IntegrationTest;
import com.broadcastmail.api.TestContainersConfiguration;
import com.broadcastmail.api.TestSecurityConfig;
import com.broadcastmail.api.supabase.management.SupabaseAccessTokens;
import com.broadcastmail.api.supabase.management.SupabaseManagementClient;
import com.broadcastmail.api.supabase.management.dto.SupabaseProject;
import com.broadcastmail.api.support.CampaignTestFixtures;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.campaign.CampaignStatus;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import com.broadcastmail.common.session.SessionRepository;
import com.broadcastmail.common.connection.ConnectionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockCookie;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestContainersConfiguration.class, TestSecurityConfig.class})
@ActiveProfiles("test")
class MeControllerTest extends IntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private ConnectionRepository connectionRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @MockitoBean
    private SupabaseManagementClient supabaseManagementClient;

    @MockitoBean
    private SupabaseAccessTokens supabaseAccessTokens;

    private Account account;

    @BeforeEach
    void setUp() {
        account = accountRepository.save(CampaignTestFixtures.account().build());
        sessionRepository.save(CampaignTestFixtures.session(account.getId()).build());
    }

    @AfterEach
    void tearDown() {
        connectionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void shouldReturnAccountDetailsWithConnectionName() {
        // Given
        var connection = connectionRepository.save(CampaignTestFixtures.connection(account.getId()).build());

        // When
        var response = mockMvc.get()
                .uri("/api/v1/me")
                .cookie(new MockCookie("bm_session", CampaignTestFixtures.TEST_SESSION_TOKEN))
                .exchange();

        // Then
        assertThat(response).hasStatus(200);
        assertThat(response).bodyJson()
                .extractingPath("$.email")
                .asString()
                .isEqualTo(account.getEmail());
        assertThat(response).bodyJson()
                .extractingPath("$.plan")
                .asString()
                .isEqualTo(account.getPlan().name());
        assertThat(response).bodyJson()
                .extractingPath("$.connectionName")
                .asString()
                .isEqualTo("test-ref");
        assertThat(response).bodyJson()
                .extractingPath("$.projectName")
                .asString()
                .isEqualTo(connection.getName());
        // Then: no app.feedback.* in the test config, so feedback stays off
        assertThat(response).bodyJson()
                .extractingPath("$.feedbackEnabled")
                .asBoolean()
                .isFalse();
        assertThat(response).bodyJson()
                .extractingPath("$.analyticsId")
                .asString()
                .isEqualTo(account.getId().toString());
    }

    @Test
    void shouldHealProjectNameWhenStoredNameIsStillTheRef() {
        // Given — a connection created before names were stored
        var connection = connectionRepository.save(
                CampaignTestFixtures.connection(account.getId()).name("test-ref").build());
        when(supabaseAccessTokens.forAccount(account.getId())).thenReturn("token");
        when(supabaseManagementClient.listProjects("token")).thenReturn(List.of(
                new SupabaseProject("test-ref", "My Project", "ACTIVE_HEALTHY", "2026-01-01", "eu-west-1", "aws")));

        // When
        var response = mockMvc.get().uri("/api/v1/me").cookie(new MockCookie("bm_session", CampaignTestFixtures.TEST_SESSION_TOKEN)).exchange();

        // Then
        assertThat(response).hasStatus(200);
        assertThat(response).bodyJson().extractingPath("$.projectName").asString().isEqualTo("My Project");
        assertThat(response).bodyJson().extractingPath("$.connectionName").asString().isEqualTo("test-ref");
        assertThat(connectionRepository.findById(connection.getId()).orElseThrow().getName()).isEqualTo("My Project");
    }

    @Test
    void shouldKeepTheRefWhenTheNameLookupFails() {
        // Given
        var connection = connectionRepository.save(
                CampaignTestFixtures.connection(account.getId()).name("test-ref").build());
        when(supabaseAccessTokens.forAccount(account.getId())).thenThrow(new IllegalStateException("token revoked"));

        // When
        var response = mockMvc.get().uri("/api/v1/me").cookie(new MockCookie("bm_session", CampaignTestFixtures.TEST_SESSION_TOKEN)).exchange();

        // Then
        assertThat(response).hasStatus(200);
        assertThat(response).bodyJson().extractingPath("$.projectName").asString().isEqualTo("test-ref");
        assertThat(connectionRepository.findById(connection.getId()).orElseThrow().getName()).isEqualTo("test-ref");
    }

    @Test
    void shouldUpdateTheNameWhenTheProjectWasRenamedInSupabase() {
        // Given
        var connection = connectionRepository.save(
                CampaignTestFixtures.connection(account.getId()).name("Old Name").build());
        when(supabaseAccessTokens.forAccount(account.getId())).thenReturn("token");
        when(supabaseManagementClient.listProjects("token")).thenReturn(List.of(
                new SupabaseProject("test-ref", "New Name", "ACTIVE_HEALTHY", "2026-01-01", "eu-west-1", "aws")));

        // When
        var response = mockMvc.get().uri("/api/v1/me").cookie(new MockCookie("bm_session", CampaignTestFixtures.TEST_SESSION_TOKEN)).exchange();

        // Then
        assertThat(response).hasStatus(200);
        assertThat(response).bodyJson().extractingPath("$.projectName").asString().isEqualTo("New Name");
        assertThat(connectionRepository.findById(connection.getId()).orElseThrow().getName()).isEqualTo("New Name");
    }

    @Test
    void shouldKeepTheSavedNameWhenTheLookupFails() {
        // Given
        var connection = connectionRepository.save(
                CampaignTestFixtures.connection(account.getId()).name("My Project").build());
        when(supabaseAccessTokens.forAccount(account.getId())).thenThrow(new IllegalStateException("token revoked"));

        // When
        var response = mockMvc.get().uri("/api/v1/me").cookie(new MockCookie("bm_session", CampaignTestFixtures.TEST_SESSION_TOKEN)).exchange();

        // Then
        assertThat(response).hasStatus(200);
        assertThat(response).bodyJson().extractingPath("$.projectName").asString().isEqualTo("My Project");
        assertThat(connectionRepository.findById(connection.getId()).orElseThrow().getName()).isEqualTo("My Project");
    }

    @Test
    void shouldCheckTheNameOnlyOncePerDay() {
        // Given
        connectionRepository.save(CampaignTestFixtures.connection(account.getId()).name("My Project").build());
        when(supabaseAccessTokens.forAccount(account.getId())).thenReturn("token");
        when(supabaseManagementClient.listProjects("token")).thenReturn(List.of(
                new SupabaseProject("test-ref", "My Project", "ACTIVE_HEALTHY", "2026-01-01", "eu-west-1", "aws")));

        // When
        mockMvc.get().uri("/api/v1/me").cookie(new MockCookie("bm_session", CampaignTestFixtures.TEST_SESSION_TOKEN)).exchange();
        var response = mockMvc.get().uri("/api/v1/me").cookie(new MockCookie("bm_session", CampaignTestFixtures.TEST_SESSION_TOKEN)).exchange();

        // Then
        assertThat(response).hasStatus(200);
        verify(supabaseManagementClient, times(1)).listProjects("token");
    }

    @Test
    void shouldReturnNullConnectionNameWhenNoConnectionExists() {
        // Given — no connection created

        // When
        var response = mockMvc.get()
                .uri("/api/v1/me")
                .cookie(new MockCookie("bm_session", CampaignTestFixtures.TEST_SESSION_TOKEN))
                .exchange();

        // Then
        assertThat(response).hasStatus(200);
        assertThat(response).bodyJson()
                .extractingPath("$.connectionName")
                .isNull();
    }

    @Test
    void shouldReturn401WhenNoApiKey() {
        // When
        var response = mockMvc.get()
                .uri("/api/v1/me")
                .exchange();

        // Then
        assertThat(response).hasStatus(401);
    }

    @Test
    void shouldDeleteTheAccountAndItsConnection() {
        // Given
        connectionRepository.save(CampaignTestFixtures.connection(account.getId()).build());

        // When
        var response = mockMvc.delete()
                .uri("/api/v1/me")
                .cookie(new MockCookie("bm_session", CampaignTestFixtures.TEST_SESSION_TOKEN))
                .exchange();

        // Then
        assertThat(response).hasStatus(204);
        assertThat(accountRepository.findById(account.getId())).isEmpty();
        assertThat(connectionRepository.findByAccountId(account.getId())).isEmpty();
    }

    @Test
    void shouldReturn401WhenDeletingWithoutApiKey() {
        // When
        var response = mockMvc.delete()
                .uri("/api/v1/me")
                .exchange();

        // Then
        assertThat(response).hasStatus(401);
        assertThat(accountRepository.findById(account.getId())).isPresent();
    }

    @Test
    void shouldReturn409AndKeepTheAccountWhileACampaignIsSending() {
        // Given
        var connection = connectionRepository.save(CampaignTestFixtures.connection(account.getId()).build());
        var sending = campaignRepository.save(CampaignTestFixtures.draftCampaign(account.getId(), connection.getId())
                .status(CampaignStatus.SENDING)
                .build());

        // When
        var response = mockMvc.delete()
                .uri("/api/v1/me")
                .cookie(new MockCookie("bm_session", CampaignTestFixtures.TEST_SESSION_TOKEN))
                .exchange();

        // Then
        assertThat(response).hasStatus(409);
        assertThat(response).bodyJson().extractingPath("$.error").isEqualTo("CAMPAIGN_IN_PROGRESS");
        assertThat(response).bodyJson().extractingPath("$.campaignId").isEqualTo(sending.getId().toString());
        assertThat(response).bodyJson().extractingPath("$.campaignName").isEqualTo("Test Campaign");
        assertThat(accountRepository.findById(account.getId())).isPresent();
    }
}
