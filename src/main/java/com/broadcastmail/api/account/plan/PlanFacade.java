package com.broadcastmail.api.account.plan;

import com.broadcastmail.api.billing.BillingService;
import com.broadcastmail.api.common.exceptions.PlanFeatureException;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.plan.PlanFeature;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.campaign.recipient.CampaignRecipientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.hibernate.engine.transaction.internal.jta.JtaStatusHelper.isActive;

@Component
@RequiredArgsConstructor
public class PlanFacade {
    private final BillingService billingService;
    private final CampaignRecipientRepository campaignRecipientRepository;

    public void enforceFeature(Account account, PlanFeature featrue) {
        if (!isActiveSubscription(account)) {
            throw new PlanFeatureException(featrue);
        }
        if(!account.getPlan().strategy().has(featrue)) {
            throw new PlanFeatureException(featrue);
        }
    }

    public void enforceRecipientLimit(Account account, int newRecipients) {
        if(isActiveSubscription(account)) {
            return;
        }
        long currentRecipients = campaignRecipientRepository.countUniqueRecipientsSince(
                account.getId(),
                OffsetDateTime.now(ZoneId.systemDefault()).minusDays(30)
        );
        account.getPlan().strategy().checkRecipientLimit(currentRecipients, newRecipients);
    }

    private boolean isActiveSubscription(Account account) {
        return billingService.isSubscriptionActive(account);
    }
}
