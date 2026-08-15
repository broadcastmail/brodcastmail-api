package com.broadcastmail.api.account.metrics;

import com.broadcastmail.api.TestContainersConfiguration;
import com.broadcastmail.api.TestSecurityConfig;
import com.broadcastmail.api.support.CampaignTestFixtures;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.campaign.CampaignStatus;
import com.broadcastmail.common.campaign.recipient.CampaignRecipient;
import com.broadcastmail.common.campaign.recipient.CampaignRecipientRepository;
import com.broadcastmail.common.campaign.recipient.RecipientStatus;
import com.broadcastmail.common.connection.Connection;
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

import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestContainersConfiguration.class, TestSecurityConfig.class})
@ActiveProfiles("test")
class AccountMetricsControllerTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private ConnectionRepository connectionRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CampaignRecipientRepository campaignRecipientRepository;

    private Account account;
    private Connection connection;

    @BeforeEach
    void setUp() {
        account = accountRepository.save(CampaignTestFixtures.account().build());
        connection = connectionRepository.save(CampaignTestFixtures.connection(account.getId()).build());
    }

    @AfterEach
    void tearDown() {
        campaignRecipientRepository.deleteAll();
        campaignRepository.deleteAll();
        connectionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void shouldReturn200WithMetrics() {
        // When
        var response = mockMvc.get()
                .uri("/api/v1/account/metrics")
                .header("X-API-Key", CampaignTestFixtures.TEST_API_KEY)
                .exchange();

        // Then
        assertThat(response).hasStatus(200);
        assertThat(response).bodyJson().extractingPath("$.recipientsLimit").asNumber().isEqualTo(500);
    }

    @Test
    void shouldReturnAudienceFromConnection() {
        // Given
        connection.setEstimatedUserCount(312);
        connectionRepository.save(connection);

        // When
        var response = mockMvc.get()
                .uri("/api/v1/account/metrics")
                .header("X-API-Key", CampaignTestFixtures.TEST_API_KEY)
                .exchange();

        // Then
        assertThat(response).bodyJson().extractingPath("$.audience").asNumber().isEqualTo(312);
    }

    @Test
    void shouldReturnDeliveryStatsFromSentCampaigns() {
        // Given
        campaignRepository.save(CampaignTestFixtures.draftCampaign(account.getId(), connection.getId())
                .status(CampaignStatus.SENT)
                .sentAt(OffsetDateTime.now(ZoneId.systemDefault()).minusDays(1))
                .recipientCount(100)
                .deliveredCount(95)
                .build());

        // When
        var response = mockMvc.get()
                .uri("/api/v1/account/metrics")
                .header("X-API-Key", CampaignTestFixtures.TEST_API_KEY)
                .exchange();

        // Then
        assertThat(response).bodyJson().extractingPath("$.totalDeliveredThisMonth").asNumber().isEqualTo(95);
        assertThat(response).bodyJson().extractingPath("$.deliveryRate").asNumber().isEqualTo(95.0);
    }

    @Test
    void shouldNotCountCampaignsOlderThan30Days() {
        // Given
        campaignRepository.save(CampaignTestFixtures.draftCampaign(account.getId(), connection.getId())
                .status(CampaignStatus.SENT)
                .sentAt(OffsetDateTime.now(ZoneId.systemDefault()).minusDays(31))
                .recipientCount(100)
                .deliveredCount(95)
                .build());

        // When
        var response = mockMvc.get()
                .uri("/api/v1/account/metrics")
                .header("X-API-Key", CampaignTestFixtures.TEST_API_KEY)
                .exchange();

        // Then
        assertThat(response).bodyJson().extractingPath("$.totalDeliveredThisMonth").asNumber().isEqualTo(0);
    }

    @Test
    void shouldReturnRecipientsUsedThisPeriod() {
        // Given
        Campaign campaign = campaignRepository.save(CampaignTestFixtures.draftCampaign(account.getId(), connection.getId())
                .status(CampaignStatus.SENT).build());

        campaignRecipientRepository.save(CampaignRecipient.builder()
                .campaignId(campaign.getId())
                .externalUserId("user-1")
                .email("user1@example.com")
                .status(RecipientStatus.QUEUED)
                .idempotencyKey(campaign.getId() + ":user-1")
                .build());

        // When
        var response = mockMvc.get()
                .uri("/api/v1/account/metrics")
                .header("X-API-Key", CampaignTestFixtures.TEST_API_KEY)
                .exchange();

        // Then
        assertThat(response).bodyJson().extractingPath("$.recipientsUsedThisPeriod").asNumber().isEqualTo(1);
    }

    @Test
    void shouldReturn401WhenNoApiKey() {
        // When
        var response = mockMvc.get()
                .uri("/api/v1/account/metrics")
                .exchange();

        // Then
        assertThat(response).hasStatus(401);
    }
}