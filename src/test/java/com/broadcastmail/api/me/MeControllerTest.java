package com.broadcastmail.api.me;

import com.broadcastmail.api.TestContainersConfiguration;
import com.broadcastmail.api.TestSecurityConfig;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestContainersConfiguration.class, TestSecurityConfig.class})
@ActiveProfiles("test")
class MeControllerTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ConnectionRepository connectionRepository;

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
        connectionRepository.save(CampaignTestFixtures.connection(account.getId()).build());

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
                .isEqualTo(account.getPlan());
        assertThat(response).bodyJson()
                .extractingPath("$.connectionName")
                .asString()
                .isEqualTo("test-ref");
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
