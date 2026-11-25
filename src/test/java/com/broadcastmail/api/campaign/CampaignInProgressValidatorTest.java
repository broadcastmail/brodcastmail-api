package com.broadcastmail.api.campaign;

import com.broadcastmail.api.common.exceptions.CampaignInProgressException;
import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.campaign.CampaignStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignInProgressValidatorTest {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final List<CampaignStatus> IN_PROGRESS = List.of(CampaignStatus.RESOLVING, CampaignStatus.SENDING);

    @Mock
    private CampaignRepository campaignRepository;

    @InjectMocks
    private CampaignInProgressValidator validator;

    @Test
    void shouldPassWhenNoCampaignIsResolvingOrSending() {
        // Given
        when(campaignRepository.findByAccountIdAndStatusIn(ACCOUNT_ID, IN_PROGRESS)).thenReturn(List.of());

        // When / Then
        assertThatNoException().isThrownBy(() -> validator.validateNoneInProgress(ACCOUNT_ID));
    }

    @Test
    void shouldNameTheCampaignInProgress() {
        // Given
        Campaign sending = Campaign.builder().id(UUID.randomUUID()).name("Black Friday").status(CampaignStatus.SENDING).build();
        when(campaignRepository.findByAccountIdAndStatusIn(ACCOUNT_ID, IN_PROGRESS)).thenReturn(List.of(sending));

        // When / Then
        assertThatThrownBy(() -> validator.validateNoneInProgress(ACCOUNT_ID))
                .isInstanceOf(CampaignInProgressException.class)
                .hasMessage("CAMPAIGN_IN_PROGRESS")
                .hasFieldOrPropertyWithValue("campaignId", sending.getId())
                .hasFieldOrPropertyWithValue("campaignName", "Black Friday");
    }
}
