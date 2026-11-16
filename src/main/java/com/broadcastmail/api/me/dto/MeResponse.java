package com.broadcastmail.api.me.dto;

import com.broadcastmail.common.account.plan.PlanFeature;

import java.util.List;

public record MeResponse(
        String email,
        String plan,
        String connectionName,
        List<PlanFeature> features
) {}
