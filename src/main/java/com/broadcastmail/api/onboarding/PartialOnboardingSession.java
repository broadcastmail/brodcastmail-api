package com.broadcastmail.api.onboarding;

import com.broadcastmail.api.supabase.dto.SupabaseProject;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PartialOnboardingSession(
        String ownerEmail,
        String encryptedAccessToken,
        String encryptedRefreshToken,
        Instant tokenExpiresAt,
        Instant expiresAt,
        List<SupabaseProject> projects,
        UUID accountId
) {
    public static PartialOnboardingSession forNewUser(
            String ownerEmail,
            String encryptedAccessToken,
            String encryptedRefreshToken,
            Instant tokenExpiresAt,
            List<SupabaseProject> projects) {
        return new PartialOnboardingSession(ownerEmail, encryptedAccessToken,
                encryptedRefreshToken, tokenExpiresAt,
                Instant.now().plus(Duration.ofMinutes(30)), projects, null);
    }

    public static PartialOnboardingSession forReconfigure(
            UUID accountId,
            String encryptedAccessToken,
            String encryptedRefreshToken,
            Instant tokenExpiresAt,
            List<SupabaseProject> projects) {
        return new PartialOnboardingSession(null, encryptedAccessToken,
                encryptedRefreshToken, tokenExpiresAt,
                Instant.now().plus(Duration.ofMinutes(30)), projects, accountId);
    }

    public boolean isReconfigure() {
        return accountId != null;
    }
}
