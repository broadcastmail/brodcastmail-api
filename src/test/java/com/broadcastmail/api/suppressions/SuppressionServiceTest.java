package com.broadcastmail.api.suppressions;

import com.broadcastmail.api.IntegrationTest;
import com.broadcastmail.api.TestContainersConfiguration;
import com.broadcastmail.api.support.CampaignTestFixtures;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.campaign.recipient.CampaignRecipient;
import com.broadcastmail.common.campaign.recipient.CampaignRecipientRepository;
import com.broadcastmail.common.campaign.recipient.RecipientStatus;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import com.broadcastmail.common.suppressions.Suppression;
import com.broadcastmail.common.suppressions.SuppressionsRepository;
import com.broadcastmail.common.unsubscribe.UnsubscribeClaims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestContainersConfiguration.class)
class SuppressionServiceTest extends IntegrationTest {

    @Autowired private SuppressionService suppressionService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private ConnectionRepository connectionRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CampaignRecipientRepository campaignRecipientRepository;
    @Autowired private SuppressionsRepository suppressionsRepository;

    private Account account;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        account = accountRepository.save(CampaignTestFixtures.account().build());
        Connection connection = connectionRepository.save(CampaignTestFixtures.connection(account.getId()).build());
        campaign = campaignRepository.save(CampaignTestFixtures.draftCampaign(account.getId(), connection.getId()).build());
    }

    @AfterEach
    void tearDown() {
        suppressionsRepository.deleteAll();
        connectionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void unsubscribeWorksAfterRetentionDeletedTheCampaign() {
        // Given — the email went out, then the retention job deleted the campaign (and its recipients)
        CampaignRecipient recipient = recipient("Jane.Doe@Example.com");
        campaignRepository.deleteById(campaign.getId());

        // When
        suppressionService.unsubscribe(new UnsubscribeClaims(recipient.getId(), account.getId(), "Jane.Doe@Example.com"));

        // Then — stored exactly as the recipient had it, so future resolution matches it
        assertThat(suppressionsRepository.findByAccountId(account.getId()))
                .extracting(Suppression::getEmail, Suppression::getReason)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Jane.Doe@Example.com", "UNSUBSCRIBE"));
    }

    @Test
    void unsubscribeAlsoMarksTheRecipientWhileItExists() {
        // Given
        CampaignRecipient recipient = recipient("jane@example.com");

        // When
        suppressionService.unsubscribe(new UnsubscribeClaims(recipient.getId(), account.getId(), "jane@example.com"));

        // Then
        assertThat(campaignRecipientRepository.findById(recipient.getId()))
                .get().extracting(CampaignRecipient::getStatus).isEqualTo(RecipientStatus.UNSUBSCRIBED);
        assertThat(suppressionsRepository.findByAccountId(account.getId())).hasSize(1);
    }

    @Test
    void unsubscribingTwiceIsHarmless() {
        // Given
        CampaignRecipient recipient = recipient("jane@example.com");
        UnsubscribeClaims claims = new UnsubscribeClaims(recipient.getId(), account.getId(), "jane@example.com");

        // When
        suppressionService.unsubscribe(claims);
        suppressionService.unsubscribe(claims);

        // Then
        assertThat(suppressionsRepository.findByAccountId(account.getId())).hasSize(1);
    }

    @Test
    void unsubscribeForAnUnknownRecipientStillSuppresses() {
        suppressionService.unsubscribe(new UnsubscribeClaims(UUID.randomUUID(), account.getId(), "gone@example.com"));

        assertThat(suppressionsRepository.findByAccountId(account.getId()))
                .extracting(Suppression::getEmail).containsExactly("gone@example.com");
    }

    private CampaignRecipient recipient(String email) {
        return campaignRecipientRepository.save(CampaignRecipient.builder()
                .campaignId(campaign.getId())
                .externalUserId("user-1")
                .email(email)
                .status(RecipientStatus.SENT)
                .idempotencyKey(campaign.getId() + ":user-1")
                .build());
    }
}
