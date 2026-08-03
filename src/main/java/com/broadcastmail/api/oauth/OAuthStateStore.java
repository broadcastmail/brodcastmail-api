package com.broadcastmail.api.oauth;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OAuthStateStore {
    private final Map<String, Instant> states = new ConcurrentHashMap<>();

    public String generateAndStore()
    {
        String state = UUID.randomUUID().toString().replace("-", "");
        Instant expiry = Instant.now().plus(Duration.ofMinutes(5));
        states.put(state, expiry);
        return state;
    }
    public boolean validate(String state)
    {
        Instant expiry = states.remove(state);
        return expiry != null && Instant.now().isBefore(expiry);
    }

    @Scheduled(fixedRate = 60000) // every minute
    public void cleanExpired() {
        Instant now = Instant.now();
        states.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));
    }
}
