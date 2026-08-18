package com.broadcastmail.api.onboarding.dto;

import com.broadcastmail.api.onboarding.OnboardingSession;

public record RecapData(
        String projectRef,
        String confirmedTable,
        String fromAddress
) {
    public static RecapData empty() {
        return new RecapData(null, null, null);
    }

    public static RecapData from(OnboardingSession session)
    {
        return new RecapData(
                session.getProjectRef(),
                session.getSchemaDetails().userTable(),
                session.getResendDetails().fromAddress()
        );
    }
}
