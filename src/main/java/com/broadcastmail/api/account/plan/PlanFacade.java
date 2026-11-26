package com.broadcastmail.api.account.plan;

import com.broadcastmail.api.billing.BillingService;
import com.broadcastmail.api.campaign.filter.dto.FilterRequest;
import com.broadcastmail.api.common.exceptions.PlanFeatureException;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.plan.PlanFeature;
import com.broadcastmail.common.account.plan.PlanLimitExceededException;
import com.broadcastmail.common.campaign.recipient.CampaignRecipientRepository;
import com.broadcastmail.common.campaign.recipient.RecipientStatus;
import com.broadcastmail.common.usage.RecipientUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;


@Component
@RequiredArgsConstructor
public class PlanFacade {
    private final BillingService billingService;
    private final RecipientUsageRepository recipientUsageRepository;
    private final CampaignRecipientRepository campaignRecipientRepository;

    public void enforceFeature(Account account, PlanFeature feature) {
        if (!isActiveSubscription(account)) {
            throw new PlanFeatureException(feature);
        }
        if (!account.getPlan().strategy().has(feature)) {
            throw new PlanFeatureException(feature);
        }
    }

    public void enforceRecipientLimit(Account account, int newRecipients) {
        long currentRecipients = recipientUsageRepository.countSince(account.getId(), periodStart());
        account.getPlan().strategy().checkRecipientLimit(currentRecipients, newRecipients);
    }

    /** A retry re-sends to the campaign's failed recipients: only those not emailed this period count, like any re-send. */
    public void enforceRetryLimit(Account account, UUID campaignId) {
        long fresh = campaignRecipientRepository.countEmailsNotUsedSince(
                campaignId, RecipientStatus.FAILED, account.getId(), periodStart());
        enforceRecipientLimit(account, Math.toIntExact(fresh));
    }

    private static OffsetDateTime periodStart() {
        return OffsetDateTime.now(ZoneId.systemDefault()).minusDays(30);
    }

    public void enforceFilterShape(Account account, List<FilterRequest> filterRequests) {
        var strategy = account.getPlan().strategy();

        if (filterRequests.size() > strategy.filterConditionLimit()) {
            throw new PlanLimitExceededException();
        }

        boolean hasGroups = filterRequests.stream().anyMatch(f -> f.groupIndex() != null || f.groupLogic() != null);

        if (hasGroups && !strategy.has(PlanFeature.FILTER_GROUPS)) {
            throw new PlanFeatureException(PlanFeature.FILTER_GROUPS);
        }
    }

    private boolean isActiveSubscription(Account account) {
        return billingService.isSubscriptionActive(account);
    }
}
