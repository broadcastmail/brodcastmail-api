package com.broadcastmail.api.campaign.confirm;

import com.broadcastmail.api.campaign.confirm.dto.RecipientRow;
import com.broadcastmail.api.campaign.filter.CampaignFilterRepository;
import com.broadcastmail.api.support.CampaignTestFixtures;
import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.campaign.CampaignStatus;
import com.broadcastmail.common.campaign.recipient.CampaignRecipientRepository;
import com.broadcastmail.common.outbox.OutboxEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CampaignPersistenceServiceTest {
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID CAMPAIGN_ID = UUID.randomUUID();

    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private CampaignRecipientRepository campaignRecipientRepository;

    @Mock
    private OutboxEntryRepository outboxRepository;

    @Mock
    private CampaignFilterRepository campaignFilterRepository;
    @InjectMocks
    private CampaignPersistenceService campaignPersistenceService;

    private Campaign draftCampaign() {
        return Campaign.builder()
                .id(CAMPAIGN_ID)
                .accountId(ACCOUNT_ID)
                .status(CampaignStatus.DRAFT)
                .build();
    }

    @Test
    void shouldTransitionCampaignStatusToSending() {
        // Given
        Campaign campaign = draftCampaign();
        List<RecipientRow> resolved = CampaignTestFixtures.recipientRows(1);

        // When
        campaignPersistenceService.persistConfirmation(CAMPAIGN_ID, resolved, campaign);

        // Then
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.SENDING);
        verify(outboxRepository).saveAll(anyList());
        verify(campaignRecipientRepository).saveAll(anyList());
        verify(campaignRepository).save(campaign);
    }

    @Test
    void shouldSetRecipientCountFromResolvedList() {
        // Given
        Campaign campaign = draftCampaign();
        List<RecipientRow> resolved = CampaignTestFixtures.recipientRows(3);

        // When
        campaignPersistenceService.persistConfirmation(CAMPAIGN_ID, resolved, campaign);

        // Then
        assertThat(campaign.getRecipientCount()).isEqualTo(3);
    }

    @Test
    void shouldPersistOneRecipientRowPerResolvedUser() {
        // Given
        Campaign campaign = draftCampaign();
        List<RecipientRow> resolved = CampaignTestFixtures.recipientRows(2);

        // When
        campaignPersistenceService.persistConfirmation(CAMPAIGN_ID, resolved, campaign);

        // Then
        verify(campaignRecipientRepository).saveAll(argThat(list -> ((List<?>) list).size() == 2));
    }

    @Test
    void shouldPersistOneOutboxRowPerRecipient() {
        // Given
        Campaign campaign = draftCampaign();
        List<RecipientRow> resolved = CampaignTestFixtures.recipientRows(2);

        // When
        campaignPersistenceService.persistConfirmation(CAMPAIGN_ID, resolved, campaign);

        // Then
        verify(outboxRepository).saveAll(argThat(list -> ((List<?>) list).size() == 2));
    }
}