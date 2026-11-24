package com.broadcastmail.api.me;

import com.broadcastmail.api.IntegrationTest;
import com.broadcastmail.api.TestContainersConfiguration;
import com.broadcastmail.api.TestSecurityConfig;
import com.broadcastmail.api.supabase.management.SupabaseAccessTokens;
import com.broadcastmail.api.supabase.management.SupabaseManagementClient;
import com.broadcastmail.api.supabase.management.dto.SupabaseProject;
import com.broadcastmail.api.support.CampaignTestFixtures;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import com.broadcastmail.common.connection.ConnectionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
    private ConnectionRepository connectionRepository;

    @MockitoBean
    private SupabaseManagementClient supabaseManagementClient;

    @MockitoBean
    private SupabaseAccessTokens supabaseAccessTokens;

    private Account account;

    @BeforeEach
    void setUp() {
        account = accountRepository.save(CampaignTestFixtures.account().build());
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
                .header("X-API-Key", CampaignTestFixtures.TEST_API_KEY)
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
        var response = mockMvc.get().uri("/api/v1/me").header("X-API-Key", CampaignTestFixtures.TEST_API_KEY).exchange();

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
        var response = mockMvc.get().uri("/api/v1/me").header("X-API-Key", CampaignTestFixtures.TEST_API_KEY).exchange();

        // Then
        assertThat(response).hasStatus(200);
        assertThat(response).bodyJson().extractingPath("$.projectName").asString().isEqualTo("test-ref");
        assertThat(connectionRepository.findById(connection.getId()).orElseThrow().getName()).isEqualTo("test-ref");
    }

    @Test
    void shouldNotLookUpTheNameWhenItIsAlreadyStored() {
        // Given
        connectionRepository.save(CampaignTestFixtures.connection(account.getId()).name("My Project").build());

        // When
        var response = mockMvc.get().uri("/api/v1/me").header("X-API-Key", CampaignTestFixtures.TEST_API_KEY).exchange();

        // Then
        assertThat(response).hasStatus(200);
        assertThat(response).bodyJson().extractingPath("$.projectName").asString().isEqualTo("My Project");
        verify(supabaseManagementClient, never()).listProjects(any());
    }

    @Test
    void shouldReturnNullConnectionNameWhenNoConnectionExists() {
        // Given — no connection created

        // When
        var response = mockMvc.get()
                .uri("/api/v1/me")
                .header("X-API-Key", CampaignTestFixtures.TEST_API_KEY)
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
}
