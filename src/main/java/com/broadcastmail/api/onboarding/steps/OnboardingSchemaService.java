package com.broadcastmail.api.onboarding.steps;

import com.broadcastmail.api.onboarding.OnboardingSession;
import com.broadcastmail.api.common.exceptions.InvalidOnboardingSessionException;
import com.broadcastmail.api.connection.schema.SchemaIntrospectionService;
import com.broadcastmail.api.connection.dto.DetectedColumn;
import com.broadcastmail.api.connection.dto.SchemaIntrospectionResult;
import com.broadcastmail.api.connection.dto.SupabaseIntrospectionData;
import com.broadcastmail.api.connection.introspection.SupabaseIntrospectionService;
import com.broadcastmail.api.oauth.OAuthSessionStore;
import com.broadcastmail.api.supabase.management.ManagementSqlRunner;
import com.broadcastmail.api.supabase.management.ManagementSqlRunners;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OnboardingSchemaService {

    private final OAuthSessionStore onboardingSessionStore;
    private final SupabaseIntrospectionService supabaseIntrospectionService;
    private final SchemaIntrospectionService schemaIntrospectionService;
    private final ManagementSqlRunners managementSqlRunners;

    /**
     * Reads the schema via the Management API — no reader role or pooler involved, so it works on first load.
     * The introspection is cached on the session, so reloading the page doesn't re-scan the whole database;
     * a new OAuth session (reconnect) starts from a fresh scan.
     */
    public SchemaIntrospectionResult detect(String sessionToken) {
        OnboardingSession session = onboardingSessionStore.get(sessionToken);
        SupabaseIntrospectionData data = session.getIntrospectionData() != null
                ? session.getIntrospectionData()
                : introspect(session);
        SchemaIntrospectionResult result = schemaIntrospectionService.process(data);
        OnboardingSession updated = switch (result) {
            case SchemaIntrospectionResult.Detected detected ->
                    applyDetected(session, detected)
                            .withSchemaCandidates(null)
                            .withIntrospectionData(data);
            case SchemaIntrospectionResult.MultipleCandidates(var candidates) ->
                    session.withSchemaDetails(null).withDetectedColumns(null).withAuthColumns(null)
                            .withSchemaCandidates(candidates)
                            .withIntrospectionData(data);
            case SchemaIntrospectionResult.NotDetected _ ->
                    session.withSchemaDetails(null).withDetectedColumns(null).withAuthColumns(null)
                            .withSchemaCandidates(null)
                            .withIntrospectionData(null);
        };
        onboardingSessionStore.updateSession(sessionToken, updated);
        return result;
    }

    public SchemaIntrospectionResult.Detected selectTable(String sessionToken, String schema, String tableName) {
        OnboardingSession session = onboardingSessionStore.get(sessionToken);
        SchemaIntrospectionResult.Detected chosen = candidates(session).stream()
                .filter(c -> matchesTable(c, schema, tableName))
                .findFirst()
                .orElseThrow(InvalidOnboardingSessionException::new);

        // Candidates stay on the session so the user can switch tables (or back from "auth.users only") freely.
        onboardingSessionStore.updateSession(sessionToken, applyDetected(session, chosen));
        return chosen;
    }

    public void skipProfileTable(String sessionToken) {
        OnboardingSession session = onboardingSessionStore.get(sessionToken);
        List<DetectedColumn> authColumns = Optional.ofNullable(session.getAuthColumns())
                .or(() -> Optional.ofNullable(session.getSchemaCandidates())
                        .filter(c -> !c.isEmpty())
                        .map(c -> c.getFirst().authColumns()))
                .orElse(List.of());
        OnboardingSession updated = session
                .withSchemaDetails(new OnboardingSession.SchemaDetails(null, null, null, true))
                .withDetectedColumns(List.of())
                .withAuthColumns(authColumns);
        onboardingSessionStore.updateSession(sessionToken, updated);
    }

    public void selectColumns(String sessionToken, List<String> columnNames) {
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

    private SupabaseIntrospectionData introspect(OnboardingSession session) {
        ManagementSqlRunner sql = managementSqlRunners.forEncryptedToken(
                session.getEncryptedAccessToken(), session.getProjectRef());
        return supabaseIntrospectionService.introspect(sql);
    }

    /**
     * Tables the user may pick. Multiple-candidate detections store the list; a single detection doesn't, so it's
     * rebuilt from the cached introspection (no database call).
     */
    private List<SchemaIntrospectionResult.Detected> candidates(OnboardingSession session) {
        if (session.getSchemaCandidates() != null) return session.getSchemaCandidates();
        if (session.getIntrospectionData() == null) return List.of();
        return switch (schemaIntrospectionService.process(session.getIntrospectionData())) {
            case SchemaIntrospectionResult.Detected detected -> List.of(detected);
            case SchemaIntrospectionResult.MultipleCandidates(var candidates) -> candidates;
            case SchemaIntrospectionResult.NotDetected _ -> List.of();
        };
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
}
