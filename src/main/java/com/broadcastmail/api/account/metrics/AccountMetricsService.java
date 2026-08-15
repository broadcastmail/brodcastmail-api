package com.broadcastmail.api.account.metrics;

import com.broadcastmail.api.account.metrics.dto.AccountMetricsResponse;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.campaign.DeliveryStats;
import com.broadcastmail.common.campaign.recipient.CampaignRecipientRepository;
import com.broadcastmail.common.connection.ConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountMetricsService {

    private final ConnectionRepository connectionRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository campaignRecipientRepository;

    public AccountMetricsResponse getMetrics(UUID accountId) {
        OffsetDateTime since = OffsetDateTime.now(ZoneId.systemDefault()).minusDays(30);

        int audience = connectionRepository.findByAccountId(accountId)
                .map(c -> c.getEstimatedUserCount() != null ? c.getEstimatedUserCount() : 0)
                .orElse(0);

        String audienceSource = connectionRepository.findByAccountId(accountId)
                .map(c -> c.getUserTableSchema() + "." + c.getUserTableName())
                .orElse("auth.users");

        DeliveryStats stats = campaignRepository.sumDeliveryStatsSince(accountId, since);

        double deliveryRate = stats.getRecipients() > 0
                ? Math.round((double) stats.getDelivered() / stats.getRecipients() * 1000.0) / 10.0
                : 0.0;

        long recipientsUsed = campaignRecipientRepository
                .countUniqueRecipientsSince(accountId, since);

        return new AccountMetricsResponse(
                audience,
                audienceSource,
                stats.getDelivered(),
                deliveryRate,
                recipientsUsed,
                500
        );
    }
}