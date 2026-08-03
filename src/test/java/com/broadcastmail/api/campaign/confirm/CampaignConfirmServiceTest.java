package com.broadcastmail.api.campaign.confirm;

import com.broadcastmail.api.campaign.CampaignFilterSerializer;
import com.broadcastmail.api.campaign.CampaignService;
import com.broadcastmail.api.campaign.filter.CampaignFilterRepository;
import com.broadcastmail.api.campaign.filter.FilterQuery;
import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.common.exceptions.CampaignNotEditableException;
import com.broadcastmail.api.common.exceptions.PlanLimitExceededException;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.connection.ConnectionRepository;
import com.broadcastmail.api.support.CampaignTestFixtures;
import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.campaign.CampaignStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignConfirmServiceTest {

    @Mock
    private CampaignService campaignService;

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private ConnectionRepository connectionRepository;

    @Mock
    private CampaignFilterRepository filterRepository;

    @Mock
    private CampaignFilterSerializer filterSerializer;

    @Mock
    private RecipientResolutionService recipientResolutionService;

    @Mock
    private CampaignPersistenceService campaignPersistenceService;

    @Mock
    private EncryptionProperties encryptionProperties;

    @InjectMocks
    private CampaignConfirmService campaignConfirmService;

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID CAMPAIGN_ID = UUID.randomUUID();
    private static final String TEST_KEY = "12345678901234567890123456789012";

    private Campaign campaign(CampaignStatus status) {
        return Campaign.builder()
                .id(CAMPAIGN_ID)
                .accountId(ACCOUNT_ID)
                .status(status)
                .build();
    }

    private void stubHappyPath() {
        when(encryptionProperties.key()).thenReturn(TEST_KEY);
        when(campaignService.getCampaign(ACCOUNT_ID, CAMPAIGN_ID)).thenReturn(campaign(CampaignStatus.DRAFT));
        when(connectionRepository.findByAccountId(ACCOUNT_ID)).thenReturn(
                Optional.of(CampaignTestFixtures.connection(ACCOUNT_ID)
                        .encryptedCreds(SecurityUtil.encrypt("testpassword123", TEST_KEY))
                        .build())
        );
        when(filterRepository.findByCampaignId(CAMPAIGN_ID)).thenReturn(List.of());
        when(filterSerializer.serialize(List.of())).thenReturn(new FilterQuery("", List.of()));
        when(recipientResolutionService.resolve(anyString(), anyString(), any(), any()))
                .thenReturn(CampaignTestFixtures.recipientRows(2));
    }

    @Test
    void shouldTransitionCampaignStatusToSending() {
        // Given
        stubHappyPath();

        // When
        campaignConfirmService.confirmCampaign(ACCOUNT_ID, CAMPAIGN_ID);

        // Then
        verify(campaignPersistenceService).persistConfirmation(eq(CAMPAIGN_ID), anyList(), any(Campaign.class));
    }

    @Test
    void shouldRejectConfirmationOfNonDraftCampaign() {
        // Given
        when(campaignService.getCampaign(ACCOUNT_ID, CAMPAIGN_ID)).thenReturn(campaign(CampaignStatus.SENDING));

        // When / Then
        assertThatThrownBy(() -> campaignConfirmService.confirmCampaign(ACCOUNT_ID, CAMPAIGN_ID))
                .isInstanceOf(CampaignNotEditableException.class);
        verify(campaignPersistenceService, never()).persistConfirmation(any(), any(), any());
    }

    @Test
    void shouldThrowWhenResolvedCountExceedsPlanLimit() {
        // Given
        stubHappyPath();
        when(recipientResolutionService.resolve(anyString(), anyString(), any(), any()))
                .thenReturn(CampaignTestFixtures.recipientRows(301));

        // When / Then
        assertThatThrownBy(() -> campaignConfirmService.confirmCampaign(ACCOUNT_ID, CAMPAIGN_ID))
                .isInstanceOf(PlanLimitExceededException.class);
        verify(campaignPersistenceService, never()).persistConfirmation(any(), any(), any());
    }
}