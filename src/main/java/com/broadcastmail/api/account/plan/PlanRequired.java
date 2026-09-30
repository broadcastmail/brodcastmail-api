package com.broadcastmail.api.account.plan;

import com.broadcastmail.common.account.plan.PlanFeature;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PlanRequired {
    PlanFeature value();
}
