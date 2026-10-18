package com.broadcastmail.api.config;

import com.stripe.Stripe;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    StripeConfig(StripeProperties stripeProperties) {
        Stripe.apiKey = stripeProperties.secretKey();
    }
}
