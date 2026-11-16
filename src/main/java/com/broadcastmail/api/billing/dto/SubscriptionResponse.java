package com.broadcastmail.api.billing.dto;

import java.time.OffsetDateTime;

public record SubscriptionResponse(
        OffsetDateTime currentPeriodEnd,
        boolean cancelAtPeriodEnd
) {}
