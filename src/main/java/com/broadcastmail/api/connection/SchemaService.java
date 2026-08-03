package com.broadcastmail.api.connection;

import com.broadcastmail.api.connection.dto.SchemaIntrospectionResult;
import com.broadcastmail.api.onboarding.OnboardingSession;
import com.broadcastmail.api.onboarding.OnboardingSessionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SchemaService {

    private final OnboardingSessionStore onboardingSessionStore;
    private final SchemaIntrospectionService schemaIntrospectionService;

    public SchemaIntrospectionResult detect(String sessionToken) {
            OnboardingSession session = onboardingSessionStore.get(sessionToken);
        SchemaIntrospectionResult result = schemaIntrospectionService.introspect(
                session.getJdbcUrl(),
                session.getEncryptedRolePassword()
        );
        OnboardingSession updated = session.withSchemaDetails(
                new OnboardingSession.SchemaDetails(
                        result.userTableName(),
                        result.userTableSchema(),
                        false
                    )
                )
                .withDetectedColumns(result.filterableColumns());
        onboardingSessionStore.updateSession(sessionToken, updated);
        return result;
    }

    public void confirm(String sessionToken, List<String> columnNames) {
        OnboardingSession session = onboardingSessionStore.get(sessionToken).requireSchemaDetected()                                    ;
        OnboardingSession updated = session
                .withSchemaDetails(new OnboardingSession.SchemaDetails(
                        session.getSchemaDetails().userTable(),
                        session.getSchemaDetails().userSchema(),
                        true
                ))
                .withConfirmedColumnNames(columnNames);
        onboardingSessionStore.updateSession(sessionToken, updated);
    }
}
