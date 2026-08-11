package com.broadcastmail.api.campaign.confirm;

import com.broadcastmail.api.campaign.CampaignService;
import com.broadcastmail.api.common.exceptions.CampaignNotEditableException;
import com.broadcastmail.api.common.exceptions.ConnectionNotFoundException;
import com.broadcastmail.common.connection.ConnectionRepository;
import com.broadcastmail.api.support.CampaignTestFixtures;
import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.campaign.CampaignStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignConfirmServiceTest {

    @Mock private CampaignService campaignService;
    @Mock private CampaignRepository campaignRepository;
    @Mock private ConnectionRepository connectionRepository;

    @InjectMocks private CampaignConfirmService campaignConfirmService;

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID CAMPAIGN_ID = UUID.randomUUID();

    private Campaign campaign(CampaignStatus status) {
        return Campaign.builder()
                .id(CAMPAIGN_ID)
                .accountId(ACCOUNT_ID)
                .status(status)
                .build();
    }

    @Test
    void shouldTransitionCampaignToResolving() {
        // Given
        Campaign campaign = campaign(CampaignStatus.DRAFT);
        when(campaignService.getCampaign(ACCOUNT_ID, CAMPAIGN_ID)).thenReturn(campaign);
        when(connectionRepository.findByAccountId(ACCOUNT_ID))
                .thenReturn(Optional.of(CampaignTestFixtures.connection(ACCOUNT_ID).build()));

        // When
        campaignConfirmService.confirmCampaign(ACCOUNT_ID, CAMPAIGN_ID);

        // Then
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.RESOLVING);
        verify(campaignRepository).save(campaign);
    }

    @Test
    void shouldRejectConfirmationOfNonDraftCampaign() {
        // Given
        when(campaignService.getCampaign(ACCOUNT_ID, CAMPAIGN_ID))
                .thenReturn(campaign(CampaignStatus.SENDING));

        // When / Then
        assertThatThrownBy(() -> campaignConfirmService.confirmCampaign(ACCOUNT_ID, CAMPAIGN_ID))
                .isInstanceOf(CampaignNotEditableException.class);
        verify(campaignRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenConnectionNotFound() {
        // Given
        when(campaignService.getCampaign(ACCOUNT_ID, CAMPAIGN_ID))
                .thenReturn(campaign(CampaignStatus.DRAFT));
        when(connectionRepository.findByAccountId(ACCOUNT_ID))
                .thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> campaignConfirmService.confirmCampaign(ACCOUNT_ID, CAMPAIGN_ID))
                .isInstanceOf(ConnectionNotFoundException.class);
        verify(campaignRepository, never()).save(any());
    }
}