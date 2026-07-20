package com.broadcastemail.api.onboarding;

import java.time.Instant;

public record PartialOnboardingSession(
        String ownerEmail,
        String encryptedAccessToken,
        String encryptedRefreshToken,
        Instant tokenExpiresAt,
        Instant expiresAt

) {}
