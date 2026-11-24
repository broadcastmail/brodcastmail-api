package com.broadcastmail.api.connection;

import com.broadcastmail.api.supabase.management.ProjectNameResolver;
import com.broadcastmail.api.supabase.management.SupabaseAccessTokens;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The connection's project name for display. Connections created before the name was stored still carry the project
 * ref as their name; the first read looks the real name up once and saves it. A failed lookup is not retried for a few
 * minutes, so a revoked token doesn't add a Management API call to every request.
 */
@Service
@RequiredArgsConstructor
public class ConnectionNameService {

    private static final Duration RETRY_AFTER = Duration.ofMinutes(10);

    private final ConnectionRepository connectionRepository;
    private final ProjectNameResolver projectNameResolver;
    private final SupabaseAccessTokens supabaseAccessTokens;
    private final Map<UUID, Instant> lastAttempt = new ConcurrentHashMap<>();

    public String projectNameOf(UUID accountId, Connection connection) {
        String name = connection.getName();
        String ref = connection.getProjectRef();
        if (ref == null || name == null || !name.equals(ref) || recentlyTried(connection.getId())) {
            return name;
        }

        lastAttempt.put(connection.getId(), Instant.now());
        String resolved = projectNameResolver.resolve(ref, () -> supabaseAccessTokens.forAccount(accountId));
        if (resolved.equals(ref)) {
            return name;
        }
        connection.setName(resolved);
        connectionRepository.save(connection);
        lastAttempt.remove(connection.getId());
        return resolved;
    }

    private boolean recentlyTried(UUID connectionId) {
        Instant last = lastAttempt.get(connectionId);
        return last != null && last.isAfter(Instant.now().minus(RETRY_AFTER));
    }
}
