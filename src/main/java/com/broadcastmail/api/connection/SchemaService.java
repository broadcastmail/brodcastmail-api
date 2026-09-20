package com.broadcastmail.api.connection;

import com.broadcastmail.api.common.exceptions.ConnectionNotFoundException;
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
                    session.withSchemaDetails(new OnboardingSession.SchemaDetails(detected.userTableName(), detected.userTableSchema(), false))
                            .withDetectedColumns(detected.filterableColumns());
            case SchemaIntrospectionResult.NotDetected _ -> session.withSchemaDetails(null).withDetectedColumns(null);
        };
        onboardingSessionStore.updateSession(sessionToken, updated);
        return result;
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
                        new OnboardingSession.SchemaDetails(session.getSchemaDetails().userTable(), session.getSchemaDetails().userSchema(), true))
                .withConfirmedColumnNames(columnNames);
        onboardingSessionStore.updateSession(sessionToken, updated);
    }
}
