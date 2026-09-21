package com.broadcastmail.api.connection;

import com.broadcastmail.api.common.exceptions.ConnectionNotFoundException;
import com.broadcastmail.api.common.exceptions.InvalidOnboardingSessionException;
import com.broadcastmail.api.connection.dto.SchemaIntrospectionResult;
import com.broadcastmail.api.onboarding.OnboardingSession;
import com.broadcastmail.api.oauth.OAuthSessionStore;
import com.broadcastmail.api.supabase.SupabaseSql;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SchemaService {

    private final OAuthSessionStore onboardingSessionStore;
    private final SchemaIntrospectionService schemaIntrospectionService;
    private final ConnectionRepository connectionRepository;

    public SchemaIntrospectionResult detect(String sessionToken) {
        OnboardingSession session = onboardingSessionStore.get(sessionToken);
        SchemaIntrospectionResult result = schemaIntrospectionService.introspect(session.getJdbcUrl(), session.getEncryptedRolePassword());
        OnboardingSession updated = switch (result) {
            case SchemaIntrospectionResult.Detected detected ->
                    applyDetected(session, detected).withSchemaCandidates(null);
            case SchemaIntrospectionResult.MultipleCandidates multiple ->
                    session.withSchemaDetails(null).withDetectedColumns(null).withAuthColumns(null)
                            .withSchemaCandidates(multiple.candidates());
            case SchemaIntrospectionResult.NotDetected _ ->
                    session.withSchemaDetails(null).withDetectedColumns(null).withAuthColumns(null).withSchemaCandidates(null);
        };
        onboardingSessionStore.updateSession(sessionToken, updated);
        return result;
    }

    /**
     * Picks one of the tables offered by a prior {@code detect()} that returned
     * {@code MultipleCandidates}. Re-uses that already-introspected data instead
     * of hitting the database again.
     */
    public SchemaIntrospectionResult.Detected selectTable(String sessionToken, String schema, String tableName) {
        OnboardingSession session = onboardingSessionStore.get(sessionToken);
        if (session.getSchemaCandidates() == null) {
            throw new InvalidOnboardingSessionException();
        }
        SchemaIntrospectionResult.Detected chosen = session.getSchemaCandidates().stream()
                .filter(c -> matchesTable(c, schema, tableName))
                .findFirst()
                .orElseThrow(InvalidOnboardingSessionException::new);

        OnboardingSession updated = applyDetected(session, chosen).withSchemaCandidates(null);
        onboardingSessionStore.updateSession(sessionToken, updated);
        return chosen;
    }

    private boolean matchesTable(SchemaIntrospectionResult.Detected candidate, String schema, String tableName) {
        return candidate.userTableSchema().equals(schema) && candidate.userTableName().equals(tableName);
    }

    private OnboardingSession applyDetected(OnboardingSession session, SchemaIntrospectionResult.Detected detected) {
        return session.withSchemaDetails(new OnboardingSession.SchemaDetails(
                        detected.userTableName(), detected.userTableSchema(), detected.userIdColumn(), false))
                .withDetectedColumns(detected.filterableColumns())
                .withAuthColumns(detected.authColumns());
    }

    public SchemaIntrospectionResult detectForAccount(UUID accountId, String projectRef) {
        Connection connection = connectionRepository.findByAccountId(accountId).orElseThrow(ConnectionNotFoundException::new);

        String jdbcUrl = SupabaseSql.buildJdbcUrl(
                projectRef != null ? projectRef : connection.getProjectRef()
        );

        return schemaIntrospectionService.introspect(jdbcUrl, connection.getEncryptedCreds());
    }

    public void confirm(String sessionToken, List<String> columnNames) {
        OnboardingSession session = onboardingSessionStore.get(sessionToken).requireSchemaDetected();
        OnboardingSession updated = session.withSchemaDetails(
                        new OnboardingSession.SchemaDetails(
                                session.getSchemaDetails().userTable(),
                                session.getSchemaDetails().userSchema(),
                                session.getSchemaDetails().userIdColumn(),
                                true))
                .withConfirmedColumnNames(columnNames);
        onboardingSessionStore.updateSession(sessionToken, updated);
    }
}
