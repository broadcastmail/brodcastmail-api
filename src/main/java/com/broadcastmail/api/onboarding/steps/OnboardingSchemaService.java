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

    /** Reads the schema via the Management API — no reader role or pooler involved, so it works on first load. */
    public SchemaIntrospectionResult detect(String sessionToken) {
        OnboardingSession session = onboardingSessionStore.get(sessionToken);
        ManagementSqlRunner sql = managementSqlRunners.forEncryptedToken(
                session.getEncryptedAccessToken(), session.getProjectRef());
        SupabaseIntrospectionData data = supabaseIntrospectionService.introspect(sql);
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
        if (session.getSchemaCandidates() == null) {
            throw new InvalidOnboardingSessionException();
        }
        SchemaIntrospectionResult.Detected chosen = session.getSchemaCandidates().stream()
                .filter(c -> matchesTable(c, schema, tableName))
                .findFirst()
                .orElseThrow(InvalidOnboardingSessionException::new);

        onboardingSessionStore.updateSession(sessionToken, applyDetected(session, chosen).withSchemaCandidates(null));
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
                .withAuthColumns(authColumns)
                .withSchemaCandidates(null);
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
