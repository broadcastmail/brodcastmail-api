package com.broadcastmail.api.account.metrics;

import com.broadcastmail.api.account.metrics.dto.AccountMetricsResponse;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.campaign.DeliveryStats;
import com.broadcastmail.common.campaign.recipient.CampaignRecipientRepository;
import com.broadcastmail.common.connection.ConnectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountMetricsServiceTest {

    @Mock
    private ConnectionRepository connectionRepository;
    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private CampaignRecipientRepository campaignRecipientRepository;

    @InjectMocks
    private AccountMetricsService accountMetricsService;

    private static final UUID ACCOUNT_ID = UUID.randomUUID();

    @Test
    void shouldReturnZeroDeliveryRateWhenNoRecipients() {
        // Given
        when(connectionRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.empty());
        when(campaignRepository.sumDeliveryStatsSince(eq(ACCOUNT_ID), any()))
                .thenReturn(stats(0, 0));
        when(campaignRecipientRepository.countUniqueRecipientsSince(eq(ACCOUNT_ID), any()))
                .thenReturn(0L);

        // When
        AccountMetricsResponse result = accountMetricsService.getMetrics(ACCOUNT_ID);

        // Then
        assertThat(result.deliveryRate()).isEqualTo(0.0);
    }

    @Test
    void shouldCalculateDeliveryRateCorrectly() {
        // Given
        when(connectionRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.empty());
        when(campaignRepository.sumDeliveryStatsSince(eq(ACCOUNT_ID), any()))
                .thenReturn(stats(95, 100));
        when(campaignRecipientRepository.countUniqueRecipientsSince(eq(ACCOUNT_ID), any()))
                .thenReturn(0L);

        // When
        AccountMetricsResponse result = accountMetricsService.getMetrics(ACCOUNT_ID);

        // Then
        assertThat(result.deliveryRate()).isEqualTo(95.0);
    }

    @Test
    void shouldReturnZeroAudienceWhenNoConnection() {
        // Given
        when(connectionRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.empty());
        when(campaignRepository.sumDeliveryStatsSince(eq(ACCOUNT_ID), any()))
                .thenReturn(stats(0, 0));
        when(campaignRecipientRepository.countUniqueRecipientsSince(eq(ACCOUNT_ID), any()))
                .thenReturn(0L);

        // When
        AccountMetricsResponse result = accountMetricsService.getMetrics(ACCOUNT_ID);

        // Then
        assertThat(result.audience()).isZero();
        assertThat(result.audienceSource()).isEqualTo("auth.users");
    }

    private DeliveryStats stats(int delivered, int recipients) {
        return new DeliveryStats() {
            @Override public int getDelivered() { return delivered; }
            @Override public int getRecipients() { return recipients; }
        };
    }
}