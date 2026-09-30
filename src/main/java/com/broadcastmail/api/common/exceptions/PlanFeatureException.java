package com.broadcastmail.api.common.exceptions;

import com.broadcastmail.common.account.plan.PlanFeature;
import lombok.Getter;

@Getter
public class PlanFeatureException extends RuntimeException {
    private final PlanFeature feature;

    public PlanFeatureException(PlanFeature feature) {
        super("Feature not available on current plan: " + feature.name());
        this.feature = feature;
    }

}
