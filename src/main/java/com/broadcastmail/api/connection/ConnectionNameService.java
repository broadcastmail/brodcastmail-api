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
 * The connection's project name for display. Supabase sends no event when a project is renamed, so the name is
 * re-checked on read: at most once a day per connection, the Management API is asked for the project's name and
 * the saved one is updated if it differs. This also fills in connections created before names were stored, which
 * still carry the project ref. A failed lookup never overwrites the saved name.
 */
@Service
@RequiredArgsConstructor
public class ConnectionNameService {

    private static final Duration CHECK_EVERY = Duration.ofHours(24);

    private final ConnectionRepository connectionRepository;
    private final ProjectNameResolver projectNameResolver;
    private final SupabaseAccessTokens supabaseAccessTokens;
    private final Map<UUID, Instant> lastCheck = new ConcurrentHashMap<>();

    public String projectNameOf(UUID accountId, Connection connection) {
        String name = connection.getName();
        String ref = connection.getProjectRef();
        if (ref == null || checkedRecently(connection.getId())) {
            return name;
        }

        lastCheck.put(connection.getId(), Instant.now());
        String resolved = projectNameResolver.resolve(ref, () -> supabaseAccessTokens.forAccount(accountId));
        // The resolver returns the ref when the lookup fails; that must not replace a real saved name.
        if (resolved.equals(ref) || resolved.equals(name)) {
            return name;
        }
        connection.setName(resolved);
        connectionRepository.save(connection);
        return resolved;
    }

    private boolean checkedRecently(UUID connectionId) {
        Instant last = lastCheck.get(connectionId);
        return last != null && last.isAfter(Instant.now().minus(CHECK_EVERY));
    }
}
