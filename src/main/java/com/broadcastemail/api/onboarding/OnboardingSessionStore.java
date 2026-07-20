package com.broadcastemail.api.onboarding;

import com.broadcastemail.api.common.exceptions.InvalidOnboardingSessionException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;

@Component
public class OnboardingSessionStore {

    private final Cache<String, OnboardingSession> sessions = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    private final Cache<String, PartialOnboardingSession> partialSessions = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    public String create(OnboardingSession session) {
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, session);
        return token;
    }

    public OnboardingSession get(String token) {
        OnboardingSession session = sessions.getIfPresent(token);
        if (session == null || Instant.now().isAfter(session.getExpiresAt())) {
            throw new InvalidOnboardingSessionException();
        }
        return session;
    }

    public void updateSession(String token, OnboardingSession updated) {
        sessions.put(token, updated);
    }

    public void invalidate(String token) {
        sessions.invalidate(token);
    }

    public String createPartial(
            String ownerEmail,
            String encryptedAccessToken,
            String encryptedRefreshToken,
            Instant tokenExpiresAt
    ) {
        String token = UUID.randomUUID().toString().replace("-", "");
        partialSessions.put(token, new PartialOnboardingSession(
                ownerEmail,
                encryptedAccessToken,
                encryptedRefreshToken,
                tokenExpiresAt,
                Instant.now().plus(Duration.ofMinutes(30))
        ));
        return token;
    }

    public PartialOnboardingSession getPartial(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidOnboardingSessionException();
        }
        PartialOnboardingSession session = partialSessions.getIfPresent(token);
        if (session == null || Instant.now().isAfter(session.expiresAt())) {
            throw new InvalidOnboardingSessionException();
        }
        return session;
    }

    public void invalidatePartial(String token) {
        partialSessions.invalidate(token);
    }
}