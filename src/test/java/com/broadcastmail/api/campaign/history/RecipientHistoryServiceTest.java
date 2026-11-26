package com.broadcastmail.api.campaign.history;

import com.broadcastmail.api.IntegrationTest;
import com.broadcastmail.api.TestContainersConfiguration;
import com.broadcastmail.api.campaign.history.projection.RecipientHistoryResponse;
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
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@Import(TestContainersConfiguration.class)
class RecipientHistoryServiceTest extends IntegrationTest {

    @Autowired private RecipientHistoryService recipientHistoryService;
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
    void shouldListRecipientsOfSentCampaignsNewestFirstWithoutDrafts() {
        // Given: two sent campaigns and a draft
        Campaign older = campaign("Older", CampaignStatus.SENT, OffsetDateTime.now().minusDays(2));
        Campaign newer = campaign("Newer", CampaignStatus.PARTIALLY_FAILED, OffsetDateTime.now().minusDays(1));
        Campaign draft = campaign("Draft", CampaignStatus.DRAFT, null);
        recipient(older, "b@example.com", RecipientStatus.DELIVERED);
        recipient(older, "a@example.com", RecipientStatus.BOUNCED);
        recipient(newer, "c@example.com", RecipientStatus.FAILED);
        recipient(draft, "d@example.com", RecipientStatus.QUEUED);

        // When
        List<RecipientHistoryResponse> history =
                recipientHistoryService.listHistory(account.getId(), null, PageRequest.of(0, 20)).getContent();

        // Then: newest campaign first, then by email; the draft's recipient is left out
        assertThat(history)
                .extracting(RecipientHistoryResponse::campaignName, RecipientHistoryResponse::email,
                        RecipientHistoryResponse::externalUserId, RecipientHistoryResponse::status)
                .containsExactly(
                        tuple("Newer", "c@example.com", "user-c@example.com", RecipientStatus.FAILED),
                        tuple("Older", "a@example.com", "user-a@example.com", RecipientStatus.BOUNCED),
                        tuple("Older", "b@example.com", "user-b@example.com", RecipientStatus.DELIVERED));
        assertThat(history.getFirst().campaignId()).isEqualTo(newer.getId());
        assertThat(history.getFirst().campaignSentAt()).isNotNull();
    }

    @Test
    void shouldLeaveOutTheRecipientsOfTheExcludedCampaign() {
        // Given: the campaign passed as excludeCampaignId, and another sent one
        Campaign excluded = campaign("Excluded", CampaignStatus.SENT, OffsetDateTime.now());
        Campaign other = campaign("Other", CampaignStatus.SENT, OffsetDateTime.now().minusDays(1));
        recipient(excluded, "excluded@example.com", RecipientStatus.SENT);
        recipient(other, "other@example.com", RecipientStatus.SENT);

        // When
        List<RecipientHistoryResponse> history =
                recipientHistoryService.listHistory(account.getId(), excluded.getId(), PageRequest.of(0, 20)).getContent();

        // Then
        assertThat(history).extracting(RecipientHistoryResponse::email).containsExactly("other@example.com");
    }

    private Campaign campaign(String name, CampaignStatus status, OffsetDateTime sentAt) {
        return campaignRepository.save(CampaignTestFixtures.draftCampaign(account.getId(), connection.getId())
                .name(name).status(status).sentAt(sentAt).build());
    }

    private void recipient(Campaign campaign, String email, RecipientStatus status) {
        campaignRecipientRepository.save(CampaignRecipient.builder()
                .campaignId(campaign.getId())
                .externalUserId("user-" + email)
                .email(email)
                .status(status)
                .idempotencyKey(campaign.getId() + ":" + email)
                .build());
    }
}
