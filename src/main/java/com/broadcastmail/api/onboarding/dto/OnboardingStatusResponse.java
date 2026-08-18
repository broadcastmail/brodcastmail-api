package com.broadcastmail.api.onboarding.dto;

import com.broadcastmail.api.onboarding.OnboardingStep;

public record OnboardingStatusResponse(
        OnboardingStep step,
        RecapData recapData
        ) {
    public OnboardingStatusResponse(OnboardingStep step) {
        this(step, RecapData.empty());
    }
}

