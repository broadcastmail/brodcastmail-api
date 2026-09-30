package com.broadcastmail.api.campaign.confirm;

import com.broadcastmail.api.TestContainersConfiguration;
import com.broadcastmail.api.campaign.CampaignService;
import com.broadcastmail.api.common.exceptions.CampaignNotEditableException;
import com.broadcastmail.api.common.exceptions.CampaignNotRetryableException;
import com.broadcastmail.api.common.exceptions.ConnectionNotFoundException;
import com.broadcastmail.common.account.AccountRepository;
import com.broadcastmail.common.campaign.filter.CampaignFilterRepository;
import com.broadcastmail.common.connection.ConnectionRepository;
import com.broadcastmail.api.support.CampaignTestFixtures;
import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignRetryRepository;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.campaign.CampaignStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Import(TestContainersConfiguration.class)
class CampaignConfirmServiceTest {

    @Mock
    private CampaignService campaignService;
    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private ConnectionRepository connectionRepository;
    @Mock
    private CampaignRetryRepository retryCampaignRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CampaignFilterRepository campaignFilterRepository;

    @InjectMocks
    private CampaignConfirmService campaignConfirmService;

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID CAMPAIGN_ID = UUID.randomUUID();
    private static final UUID RETRY_CAMPAIGN_ID = UUID.randomUUID();

    private Campaign campaign(CampaignStatus status) {
        return Campaign.builder().id(CAMPAIGN_ID).accountId(ACCOUNT_ID).connectionId(UUID.randomUUID()).name("Newsletter").subject("Hello")
                .bodyHtml("<p>Hi</p>").status(status).deliveredCount(95).sentCount(100).openedCount(30)
                .bouncedCount(2).failedCount(5).build();
    }

    @Test
    void shouldTransitionCampaignToResolving() {
        // Given
        Campaign campaign = campaign(CampaignStatus.DRAFT);
        when(campaignService.getCampaign(ACCOUNT_ID, CAMPAIGN_ID)).thenReturn(campaign);
        when(connectionRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.of(CampaignTestFixtures.connection(ACCOUNT_ID).build()));
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(CampaignTestFixtures.account().build()));
        when(campaignFilterRepository.findByCampaignId(CAMPAIGN_ID)).thenReturn(List.of());
        // When
        campaignConfirmService.confirmCampaign(ACCOUNT_ID, CAMPAIGN_ID);

        // Then
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.RESOLVING);
        verify(campaignRepository).save(campaign);
    }

    @Test
    void shouldRejectConfirmationOfNonDraftCampaign() {
        // Given
        when(campaignService.getCampaign(ACCOUNT_ID, CAMPAIGN_ID)).thenReturn(campaign(CampaignStatus.SENDING));

        // When / Then
        assertThatThrownBy(() -> campaignConfirmService.confirmCampaign(ACCOUNT_ID, CAMPAIGN_ID)).isInstanceOf(CampaignNotEditableException.class);
        verify(campaignRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenConnectionNotFound() {
        // Given
        when(campaignService.getCampaign(ACCOUNT_ID, CAMPAIGN_ID)).thenReturn(campaign(CampaignStatus.DRAFT));
        when(connectionRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> campaignConfirmService.confirmCampaign(ACCOUNT_ID, CAMPAIGN_ID)).isInstanceOf(ConnectionNotFoundException.class);
        verify(campaignRepository, never()).save(any());
    }

    @Test
    void shouldRestartResolutionWhenFailedCampaignHasNoFailedRecipients() {
        // Given
        Campaign campaign = campaign(CampaignStatus.FAILED);
        givenRetryableCampaign(campaign);
        givenFailedRecipients(0);

        // When
        Optional<Campaign> retry = campaignConfirmService.retryFailed(ACCOUNT_ID, CAMPAIGN_ID);

        // Then
        assertThat(retry).isEmpty();
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.RESOLVING);
        assertThat(campaign).extracting(Campaign::getDeliveredCount, Campaign::getSentCount, Campaign::getOpenedCount,
                Campaign::getBouncedCount, Campaign::getFailedCount).containsOnly(0);
        verify(campaignRepository).save(campaign);
        verify(retryCampaignRepository, never()).copyFailedRecipients(any(), any());
        verify(retryCampaignRepository, never()).enqueueRecipients(any());
    }

    @Test
    void shouldCreateRetryCampaignWhenFailedCampaignHasFailedRecipients() {
        // Given
        Campaign campaign = campaign(CampaignStatus.FAILED);
        givenRetryableCampaign(campaign);
        givenFailedRecipients(4);
        givenRetryRecipientsCopied(4);
        givenSavedRetryCampaign();

        // When
        Optional<Campaign> retry = campaignConfirmService.retryFailed(ACCOUNT_ID, CAMPAIGN_ID);

        // Then
        assertThat(retry).isPresent();
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.FAILED);
        verify(retryCampaignRepository).copyFailedRecipients(CAMPAIGN_ID, RETRY_CAMPAIGN_ID);
    }

    @Test
    void shouldCreateRetryCampaignForFailedRecipientsOfPartiallyFailedCampaign() {
        // Given
        Campaign campaign = campaign(CampaignStatus.PARTIALLY_FAILED);
        givenRetryableCampaign(campaign);
        givenFailedRecipients(3);
        givenRetryRecipientsCopied(3);
        givenSavedRetryCampaign();

        // When
        Campaign retry = campaignConfirmService.retryPartiallyFailed(ACCOUNT_ID, CAMPAIGN_ID);

        // Then
        assertThat(retry.getId()).isEqualTo(RETRY_CAMPAIGN_ID);
        assertThat(retry.getStatus()).isEqualTo(CampaignStatus.SENDING);
        assertThat(retry.getRecipientCount()).isEqualTo(3);
        assertThat(retry.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(retry.getName()).isEqualTo("Newsletter (retry)");
        assertThat(retry.getRetryOfCampaignId()).isEqualTo(CAMPAIGN_ID);
        assertThat(retry.getSentCount()).isZero();
        verify(retryCampaignRepository).copyFailedRecipients(CAMPAIGN_ID, RETRY_CAMPAIGN_ID);
        verify(retryCampaignRepository).enqueueRecipients(RETRY_CAMPAIGN_ID);
    }

    @Test
    void shouldLeaveOriginalCampaignUntouchedWhenRetryingPartiallyFailed() {
        // Given
        Campaign campaign = campaign(CampaignStatus.PARTIALLY_FAILED);
        givenRetryableCampaign(campaign);
        givenFailedRecipients(3);
        givenRetryRecipientsCopied(3);
        givenSavedRetryCampaign();

        // When
        campaignConfirmService.retryPartiallyFailed(ACCOUNT_ID, CAMPAIGN_ID);

        // Then
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.PARTIALLY_FAILED);
        assertThat(campaign.getSentCount()).isEqualTo(100);
        assertThat(campaign.getFailedCount()).isEqualTo(5);
        verify(campaignRepository, never()).save(campaign);
    }

    @Test
    void shouldRejectRetryOfPartiallyFailedCampaignWithoutFailedRecipients() {
        // Given
        givenRetryableCampaign(campaign(CampaignStatus.PARTIALLY_FAILED));
        givenFailedRecipients(0);

        // When / Then
        assertThatThrownBy(() -> campaignConfirmService.retryPartiallyFailed(ACCOUNT_ID, CAMPAIGN_ID))
                .isInstanceOf(CampaignNotRetryableException.class);
        verify(retryCampaignRepository).countFailedRecipients(CAMPAIGN_ID);
        verify(retryCampaignRepository, never()).copyFailedRecipients(any(), any());
        verify(campaignRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectCreatingMoreThanOneRetryCampaignForSameCampaign() {
        Campaign campaign = campaign(CampaignStatus.PARTIALLY_FAILED);
        givenRetryableCampaign(campaign);
        givenFailedRecipients(3);
        when(retryCampaignRepository.hasRetryCampaign(CAMPAIGN_ID)).thenReturn(true);

        assertThatThrownBy(() -> campaignConfirmService.retryPartiallyFailed(ACCOUNT_ID, CAMPAIGN_ID))
                .isInstanceOf(CampaignNotRetryableException.class);

        verify(campaignRepository, never()).saveAndFlush(any());
        verify(retryCampaignRepository, never()).copyFailedRecipients(any(), any());
    }

    @Test
    void shouldRejectRetryFailedForNonFailedCampaign() {
        // Given
        givenRetryableCampaign(campaign(CampaignStatus.PARTIALLY_FAILED));

        // When / Then
        assertThatThrownBy(() -> campaignConfirmService.retryFailed(ACCOUNT_ID, CAMPAIGN_ID))
                .isInstanceOf(CampaignNotRetryableException.class);
    }

    @Test
    void shouldRejectRetryPartiallyFailedForNonPartiallyFailedCampaign() {
        // Given
        givenRetryableCampaign(campaign(CampaignStatus.FAILED));

        // When / Then
        assertThatThrownBy(() -> campaignConfirmService.retryPartiallyFailed(ACCOUNT_ID, CAMPAIGN_ID))
                .isInstanceOf(CampaignNotRetryableException.class);
    }

    private void givenFailedRecipients(long failed) {
        when(retryCampaignRepository.countFailedRecipients(CAMPAIGN_ID)).thenReturn(failed);
    }

    private void givenRetryRecipientsCopied(int count) {
        when(retryCampaignRepository.copyFailedRecipients(CAMPAIGN_ID, RETRY_CAMPAIGN_ID))
                .thenReturn(count);
        when(retryCampaignRepository.enqueueRecipients(RETRY_CAMPAIGN_ID))
                .thenReturn(count);
    }

    private void givenRetryableCampaign(Campaign campaign) {
        when(retryCampaignRepository.findByAccountIdAndIdForUpdate(ACCOUNT_ID, CAMPAIGN_ID))
                .thenReturn(Optional.of(campaign));
    }

    private void givenSavedRetryCampaign() {
        when(campaignRepository.saveAndFlush(any(Campaign.class))).thenAnswer(invocation -> {
            Campaign saved = invocation.getArgument(0);
            saved.setId(RETRY_CAMPAIGN_ID);
            return saved;
        });
    }
}
