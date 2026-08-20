package com.broadcastmail.api.onboarding;

import com.broadcastmail.api.supabase.dto.SupabaseProject;

import java.time.Instant;
import java.util.List;

public record PartialOnboardingSession(
        String ownerEmail,
        String encryptedAccessToken,
        String encryptedRefreshToken,
        Instant tokenExpiresAt,
        Instant expiresAt,
        List<SupabaseProject> projects
) {}
