package com.broadcastmail.api.billing;

import com.broadcastmail.api.billing.dto.SubscriptionResponse;
import com.broadcastmail.api.config.AppProperties;
import com.broadcastmail.api.config.StripeProperties;
import com.broadcastmail.common.account.Account;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.param.SubscriptionListParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingService {
    private final StripeProperties stripeProperties;
    private final AppProperties appProperties;

    public String createCheckoutSession(UUID accountId) throws StripeException {
        SessionCreateParams params =
                SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                        .setSuccessUrl(appProperties.frontend().url() + "/billing/success")
                        .setCancelUrl(appProperties.frontend().url() + "/billing/cancel")
                        .putMetadata("accountId", accountId.toString())
                        .putMetadata("plan", "pro")
                        .addLineItem(SessionCreateParams.LineItem.builder()
                                .setPrice(stripeProperties.proPriceId())
                                .setQuantity(1L)
                                .build())
                        .build();

        return Session.create(params).getUrl();
    }

    public String createPortalSession(String stripeCustomerId) throws StripeException {
        SessionCreateParams params = SessionCreateParams.builder()
                .setCustomer(stripeCustomerId)
                .setReturnUrl(appProperties.frontend().url() + "/billing?from=portal")
                .build();

        return Session.create(params).getUrl();
    }

    public SubscriptionResponse getSubscription(Account account) throws StripeException {
        if (account.getStripeCustomerId() == null) {
            return new SubscriptionResponse(null, false);
        }
        SubscriptionListParams params = SubscriptionListParams.builder()
                .setCustomer(account.getStripeCustomerId())
                .setLimit(1L)
                .build();
        List<Subscription> subs = Subscription.list(params).getData();
        if (subs.isEmpty()) {
            return new SubscriptionResponse(null, false);
        }
        Subscription sub = subs.get(0);
        OffsetDateTime periodEnd = sub.getCancelAt() != null
                ? OffsetDateTime.ofInstant(Instant.ofEpochSecond(sub.getCancelAt()), ZoneOffset.UTC)
                : null;
        return new SubscriptionResponse(periodEnd, Boolean.TRUE.equals(sub.getCancelAtPeriodEnd()));
    }

    private static final Set<String> ACTIVE_STATUSES = Set.of("active", "trialing");

    public boolean isSubscriptionActive(Account account) {
        return account.getStripeSubscriptionStatus() != null
                && ACTIVE_STATUSES.contains(account.getStripeSubscriptionStatus());
    }
}
