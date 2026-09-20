package com.broadcastmail.api.oauth;

import jakarta.annotation.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OAuthStateStore {

    private record StateEntry(Instant expiry, @Nullable UUID accountId) {}

    private final Map<String, StateEntry> states = new ConcurrentHashMap<>();

    public String generateAndStore(@Nullable UUID accountId) {
        String state = UUID.randomUUID().toString().replace("-", "");
        states.put(state, new StateEntry(Instant.now().plus(Duration.ofMinutes(5)), accountId));
        return state;
    }

    public record ValidationResult(boolean valid, @Nullable UUID accountId) {}
    public ValidationResult validateAndGet(String state)
    {
        StateEntry entry = states.remove(state);
        if (entry == null || Instant.now().isAfter(entry.expiry())) {
            return new ValidationResult(false, null);
        }
        return new ValidationResult(true, entry.accountId());
    }

    @Scheduled(fixedRate = 60000) // every minute
    public void cleanExpired() {
        Instant now = Instant.now();
        states.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiry()));
    }
}
