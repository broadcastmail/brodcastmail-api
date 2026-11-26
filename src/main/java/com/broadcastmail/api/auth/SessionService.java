package com.broadcastmail.api.auth;

import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.common.session.Session;
import com.broadcastmail.common.session.SessionRepository;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionService {

    public static final Duration IDLE_TIMEOUT = Duration.ofDays(30);
    static final Duration ABSOLUTE_TIMEOUT = Duration.ofDays(90);
    static final Duration TOUCH_INTERVAL = Duration.ofHours(1);
    private static final int MAX_USER_AGENT_LENGTH = 512;

    private final SessionRepository sessionRepository;

    public record ValidSession(UUID accountId, OffsetDateTime refreshedExpiresAt) {}

    @Transactional
    public String create(UUID accountId, @Nullable String userAgent) {
        String token = SecurityUtil.generateSessionToken();
        OffsetDateTime now = OffsetDateTime.now();
        sessionRepository.save(Session.builder()
                .accountId(accountId)
                .tokenHash(SecurityUtil.sha256(token))
                .createdAt(now)
                .lastSeenAt(now)
                .expiresAt(now.plus(IDLE_TIMEOUT))
                .userAgent(userAgent == null || userAgent.length() <= MAX_USER_AGENT_LENGTH
                        ? userAgent : userAgent.substring(0, MAX_USER_AGENT_LENGTH))
                .build());
        return token;
    }

    @Transactional
    public Optional<ValidSession> validate(String token) {
        OffsetDateTime now = OffsetDateTime.now();
        return sessionRepository.findByTokenHashAndExpiresAtAfter(SecurityUtil.sha256(token), now)
                .map(session -> {
                    if (session.getLastSeenAt().isAfter(now.minus(TOUCH_INTERVAL))) {
                        return new ValidSession(session.getAccountId(), null);
                    }
                    OffsetDateTime idleExpiry = now.plus(IDLE_TIMEOUT);
                    OffsetDateTime absoluteExpiry = session.getCreatedAt().plus(ABSOLUTE_TIMEOUT);
                    session.setLastSeenAt(now);
                    session.setExpiresAt(idleExpiry.isBefore(absoluteExpiry) ? idleExpiry : absoluteExpiry);
                    sessionRepository.save(session);
                    return new ValidSession(session.getAccountId(), session.getExpiresAt());
                });
    }

    public void revoke(String token) {
        sessionRepository.deleteByTokenHash(SecurityUtil.sha256(token));
    }
}
