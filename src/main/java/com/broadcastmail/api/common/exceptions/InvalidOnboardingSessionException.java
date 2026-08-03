package com.broadcastmail.api.common.exceptions;

public class InvalidOnboardingSessionException extends RuntimeException {
    public InvalidOnboardingSessionException() {
        super("Invalid or expired onboarding session");
    }
}
