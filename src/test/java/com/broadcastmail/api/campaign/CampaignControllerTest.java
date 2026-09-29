package com.broadcastmail.api.campaign;

import com.broadcastmail.api.TestContainersConfiguration;
import com.broadcastmail.api.TestSecurityConfig;
import com.broadcastmail.api.campaign.dto.CreateCampaignRequest;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import com.broadcastmail.common.campaign.CampaignStatus;
import com.broadcastmail.common.campaign.recipient.CampaignRecipient;
import com.broadcastmail.common.campaign.recipient.CampaignRecipientRepository;
import com.broadcastmail.common.campaign.recipient.RecipientStatus;
import com.broadcastmail.common.outbox.OutboxEntryRepository;
import com.broadcastmail.common.outbox.OutboxStatus;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import com.broadcastmail.api.support.CampaignTestFixtures;
import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static com.broadcastmail.api.support.CampaignTestFixtures.TEST_API_KEY;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestContainersConfiguration.class, TestSecurityConfig.class})
class CampaignControllerTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CampaignRepository campaignRepository;
    @Autowired
    private CampaignRecipientRepository campaignRecipientRepository;
    @Autowired
    private OutboxEntryRepository outboxEntryRepository;
    @Autowired
    private ConnectionRepository connectionRepository;

    private Account account;
    private Connection connection;



    @BeforeEach
    void setUp() {
        account = accountRepository.save(CampaignTestFixtures.account().build());
        connection = connectionRepository.save(CampaignTestFixtures.connection(account.getId()).build());
    }

    @AfterEach
    void tearDown() {
        outboxEntryRepository.deleteAll();
        connectionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void shouldReturn201WithCampaignOnCreate() throws Exception {
        // Given
        CreateCampaignRequest request = new CreateCampaignRequest("Newsletter", "Hello", "<p>Hi</p>", null, connection.getId(),null);
        String requestBody = objectMapper.writeValueAsString(request);

        // When
        var response = authedPost("/api/v1/campaigns")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .exchange();

        // Then
        assertThat(response).hasStatus(201);
    }

    @Test
    void shouldReturn404WhenCampaignDoesNotExist() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When
        var response = authedGet("/api/v1/campaigns/" + nonExistentId)
                .exchange();

        // Then
        assertThat(response).hasStatus(404);
    }

    @Test
    void shouldReturn204OnDelete() {
        // Given
        Campaign campaign = campaignRepository.save(
                CampaignTestFixtures.draftCampaign(account.getId(), connection.getId()).build());

        // When
        var response = authedDelete("/api/v1/campaigns/" + campaign.getId())
                .exchange();

        // Then
        assertThat(response).hasStatus(204);
    }

    @Test
    void shouldReturn401WhenNotAuthenticated() {
        // When
        var response = mockMvc.get()
                .uri("/api/v1/campaigns")
                .exchange();

        // Then
        assertThat(response).hasStatus(401);
    }

    @Test
    void shouldReturn202WhenRetryingFailedCampaign() {
        // Given
        Campaign campaign = campaignRepository.save(
                CampaignTestFixtures.draftCampaign(account.getId(), connection.getId())
                        .status(CampaignStatus.FAILED)
                        .build());

        // When
        var response = authedPost("/api/v1/campaigns/" + campaign.getId() + "/retry")
                .exchange();

        // Then
        assertThat(response).hasStatus(202);
    }


    @Test
    void shouldCreateRetryCampaignForFailedRecipientsOnlyWhenRetryingPartiallyFailedCampaign() {
        // Given
        Campaign campaign = campaignRepository.save(
                CampaignTestFixtures.draftCampaign(account.getId(), connection.getId())
                        .status(CampaignStatus.PARTIALLY_FAILED)
                        .build());
        campaignRecipientRepository.save(recipient(campaign, "failed-user", RecipientStatus.FAILED));
        campaignRecipientRepository.save(recipient(campaign, "delivered-user", RecipientStatus.DELIVERED));

        // When
        var response = authedPost("/api/v1/campaigns/" + campaign.getId() + "/recipients/retry-failed")
                .exchange();

        // Then
        assertThat(response).hasStatus(202);
        Campaign retry = campaignRepository.findByAccountId(account.getId()).stream()
                .filter(candidate -> !candidate.getId().equals(campaign.getId()))
                .findFirst()
                .orElseThrow();
        UUID retryCampaignId = retry.getId();
        assertThat(retry.getRetryOfCampaignId()).isEqualTo(campaign.getId());
        assertThat(retry.getStatus()).isEqualTo(CampaignStatus.SENDING);
        assertThat(retry.getRecipientCount()).isEqualTo(1);
        assertThat(campaignRecipientRepository.findByCampaignId(retryCampaignId, Pageable.unpaged()))
                .singleElement()
                .satisfies(copy -> {
                    assertThat(copy.getEmail()).isEqualTo("failed-user@example.com");
                    assertThat(copy.getStatus()).isEqualTo(RecipientStatus.QUEUED);
                    assertThat(outboxEntryRepository.findAll())
                            .filteredOn(entry -> entry.getCampaignRecipientId().equals(copy.getId()))
                            .singleElement()
                            .satisfies(entry -> assertThat(entry.getStatus()).isEqualTo(OutboxStatus.PENDING));
                });

        assertThat(campaignRepository.findById(campaign.getId()).orElseThrow().getStatus())
                .isEqualTo(CampaignStatus.PARTIALLY_FAILED);
        assertThat(campaignRecipientRepository.findByCampaignIdAndStatus(
                campaign.getId(), RecipientStatus.FAILED, Pageable.unpaged())).hasSize(1);

        assertThat(authedPost("/api/v1/campaigns/" + campaign.getId() + "/recipients/retry-failed")
                .exchange()).hasStatus(409);
    }

    @Test
    void shouldReturn409WhenRetryingPartiallyFailedCampaignWithoutFailedRecipients() {
        // Given
        Campaign campaign = campaignRepository.save(
                CampaignTestFixtures.draftCampaign(account.getId(), connection.getId())
                        .status(CampaignStatus.PARTIALLY_FAILED)
                        .build());

        // When
        var response = authedPost("/api/v1/campaigns/" + campaign.getId() + "/recipients/retry-failed")
                .exchange();

        // Then
        assertThat(response).hasStatus(409);
    }

    @Test
    void shouldReturn409WhenRetryingNonFailedCampaign() {
        // Given
        Campaign campaign = campaignRepository.save(
                CampaignTestFixtures.draftCampaign(account.getId(), connection.getId())
                        .status(CampaignStatus.PARTIALLY_FAILED)
                        .build());

        // When
        var response = authedPost("/api/v1/campaigns/" + campaign.getId() + "/retry")
                .exchange();

        // Then
        assertThat(response).hasStatus(409);
        assertThat(response).bodyJson()
                .extractingPath("$.error")
                .asString()
                .isEqualTo("Campaign is not in a retryable state");
    }

    @Test
    void shouldReturn409WhenRetryingPartiallyFailedOnWrongEndpoint() {
        // Given
        Campaign campaign = campaignRepository.save(
                CampaignTestFixtures.draftCampaign(account.getId(), connection.getId())
                        .status(CampaignStatus.FAILED)
                        .build());

        // When
        var response = authedPost("/api/v1/campaigns/" + campaign.getId() + "/recipients/retry-failed")
                .exchange();

        // Then
        assertThat(response).hasStatus(409);
        assertThat(response).bodyJson()
                .extractingPath("$.error")
                .asString()
                .isEqualTo("Campaign is not in a retryable state");
    }

    // Helpers
    private CampaignRecipient recipient(Campaign campaign, String userId, RecipientStatus status) {
        return CampaignRecipient.builder()
                .campaignId(campaign.getId())
                .externalUserId(userId)
                .email(userId + "@example.com")
                .status(status)
                .idempotencyKey(campaign.getId() + ":" + userId)
                .build();
    }
    private MockMvcTester.MockMvcRequestBuilder authedGet(String uri) {
        return mockMvc.get().uri(uri).cookie(new MockCookie("bm_session", TEST_API_KEY));
    }

    private MockMvcTester.MockMvcRequestBuilder authedPost(String uri) {
        return mockMvc.post().uri(uri).cookie(new MockCookie("bm_session", TEST_API_KEY));
    }

    private MockMvcTester.MockMvcRequestBuilder authedDelete(String uri) {
        return mockMvc.delete().uri(uri).cookie(new MockCookie("bm_session", TEST_API_KEY));
    }
}