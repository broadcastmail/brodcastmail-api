package com.broadcastmail.api.billing;

import com.broadcastmail.api.config.AppProperties;
import com.broadcastmail.api.config.StripeProperties;
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

import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {
    private final StripeProperties stripeProperties;
    private final AppProperties appProperties;

    public String createCheckoutSession(UUID accountId) throws StripeException {
        Stripe.apiKey = stripeProperties.secretKey();

        SessionCreateParams params =
                SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                        .setSuccessUrl(appProperties.frontend().url() + "/dashboard?upgraded=true")
                        .setCancelUrl(appProperties.frontend().url() + "/dashboard")
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
        Stripe.apiKey = stripeProperties.secretKey();

        SessionCreateParams params = SessionCreateParams.builder()
                .setCustomer(stripeCustomerId)
                .setReturnUrl(appProperties.frontend().url() + "/dashboard")
                .build();

        return Session.create(params).getUrl();
    }

    public void cancelSubscriptionsForCustomer(String stripeCustomerId) {
        if (stripeCustomerId == null) return;
        try {
            Stripe.apiKey = stripeProperties.secretKey();
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

    private static final Set<String> ACTIVE_STATUSES = Set.of("active", "trialing");

    public boolean isSubscriptionActive(Account account) {
        return ACTIVE_STATUSES.contains(account.getStripeSubscriptionStatus());
    }
}
