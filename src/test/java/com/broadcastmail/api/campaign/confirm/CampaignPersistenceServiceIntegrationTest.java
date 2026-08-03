package com.broadcastmail.api.campaign.confirm;


import com.broadcastmail.api.TestContainersConfiguration;
import com.broadcastmail.api.account.Account;
import com.broadcastmail.api.account.AccountRepository;
import com.broadcastmail.api.campaign.confirm.dto.RecipientRow;
import com.broadcastmail.api.connection.Connection;
import com.broadcastmail.api.connection.ConnectionRepository;
import com.broadcastmail.api.support.CampaignTestFixtures;
import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.campaign.CampaignStatus;
import com.broadcastmail.common.campaign.recipient.CampaignRecipientRepository;
import com.broadcastmail.common.outbox.OutboxEntryRepository;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Import(TestContainersConfiguration.class)
class CampaignPersistenceServiceIntegrationTest {

    @Autowired
    private CampaignPersistenceService campaignPersistenceService;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private CampaignRecipientRepository campaignRecipientRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ConnectionRepository connectionRepository;

    @MockitoSpyBean
    private OutboxEntryRepository outboxEntryRepository;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        Account account = accountRepository.save(CampaignTestFixtures.account().build());
        Connection connection = connectionRepository.save(CampaignTestFixtures.connection(account.getId()).build());
        campaign = campaignRepository.save(
                CampaignTestFixtures.draftCampaign(account.getId(), connection.getId()).build());
    }

    @AfterEach
    void tearDown() {
        campaignRecipientRepository.deleteAll();
        campaignRepository.deleteAll();
        connectionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void shouldRollbackEverythingIfOutboxCreationFails() {
        // Given
        List<RecipientRow> resolved = CampaignTestFixtures.recipientRows(1);
        doThrow(new RuntimeException("outbox failure")).when(outboxEntryRepository).saveAll(any());

        // When
        ThrowableAssert.ThrowingCallable action = () ->
                campaignPersistenceService.persistConfirmation(campaign.getId(), resolved, campaign);

        // Then
        assertThatThrownBy(action).isInstanceOf(RuntimeException.class);
        assertThat(campaignRecipientRepository.findByCampaignId(campaign.getId(), Pageable.unpaged()))
                .isEmpty();
        assertThat(campaignRepository.findById(campaign.getId()))
                .map(Campaign::getStatus)
                .hasValue(CampaignStatus.DRAFT);
    }

    @Test
    void shouldPersistRecipientsAndOutboxOnSuccess() {
        // Given
        List<RecipientRow> resolved = CampaignTestFixtures.recipientRows(2);

        // When
        campaignPersistenceService.persistConfirmation(campaign.getId(), resolved, campaign);

        // Then
        assertThat(campaignRecipientRepository.findByCampaignId(campaign.getId(), Pageable.unpaged()))
                .hasSize(2);
        assertThat(campaignRepository.findById(campaign.getId()))
                .map(Campaign::getStatus)
                .hasValue(CampaignStatus.SENDING);
    }
}