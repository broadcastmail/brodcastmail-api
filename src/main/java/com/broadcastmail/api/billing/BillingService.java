package com.broadcastmail.api.billing;

import com.broadcastmail.api.config.AppProperties;
import com.broadcastmail.api.config.StripeProperties;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

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
}
