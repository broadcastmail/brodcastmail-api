package com.broadcastmail.api.billing;

import com.broadcastmail.api.billing.dto.SubscriptionResponse;
import com.broadcastmail.api.config.AppProperties;
import com.broadcastmail.api.config.StripeProperties;
import jakarta.annotation.Nullable;
import com.broadcastmail.common.account.Account;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionCollection;
import com.stripe.model.checkout.Session;
import com.stripe.param.SubscriptionListParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {
    private final StripeProperties stripeProperties;
    private final AppProperties appProperties;

    public String createCheckoutSession(UUID accountId, boolean yearly, @Nullable String redirectOrigin) throws StripeException {
        String base = redirectOrigin != null ? redirectOrigin : appProperties.frontend().url();
        String priceId = yearly ? stripeProperties.yearlyPriceId() : stripeProperties.monthlyPriceId();

        SessionCreateParams params =
                SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                        .setSuccessUrl(base + "/billing/success")
                        .setCancelUrl(base + "/billing/cancel")
                        .putMetadata("accountId", accountId.toString())
                        .putMetadata("plan", "starter")
                        .addLineItem(SessionCreateParams.LineItem.builder()
                                .setPrice(priceId)
                                .setQuantity(1L)
                                .build())
                        .build();

        return Session.create(params).getUrl();
    }

    public String createPortalSession(String stripeCustomerId, @Nullable String redirectOrigin) throws StripeException {
        String base = redirectOrigin != null ? redirectOrigin : appProperties.frontend().url();
        SessionCreateParams params = SessionCreateParams.builder()
                .setCustomer(stripeCustomerId)
                .setReturnUrl(base + "/billing?from=portal")
                .build();

        return Session.create(params).getUrl();
    }

    public void cancelSubscriptionsForCustomer(String stripeCustomerId) {
        if (stripeCustomerId == null) return;
        try {
            SubscriptionCollection subscriptions = Subscription.list(
                    SubscriptionListParams.builder()
                            .setCustomer(stripeCustomerId)
                            .setStatus(SubscriptionListParams.Status.ACTIVE)
                            .build());
            for (Subscription sub : subscriptions.getData()) {
                sub.cancel();
            }
        } catch (Exception e) {
            log.warn("Failed to cancel Stripe subscriptions for customer {}: {}", stripeCustomerId, e.getMessage());
        }
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
        Subscription sub = subs.getFirst();
        OffsetDateTime periodEnd = sub.getCancelAt() != null
                ? OffsetDateTime.ofInstant(Instant.ofEpochSecond(sub.getCancelAt()), ZoneOffset.UTC)
                : null;
        return new SubscriptionResponse(periodEnd, Boolean.TRUE.equals(sub.getCancelAtPeriodEnd()));
    }

    private static final Set<String> ACTIVE_STATUSES = Set.of("active", "trialing");

    public boolean isSubscriptionActive(Account account) {
        return ACTIVE_STATUSES.contains(account.getStripeSubscriptionStatus());
    }
}
