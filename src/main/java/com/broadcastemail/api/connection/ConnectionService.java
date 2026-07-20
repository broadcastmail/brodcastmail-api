package com.broadcastemail.api.connection;

import com.broadcastemail.api.connection.dto.SchemaIntrospectionResult;
import com.broadcastemail.api.filterablecolumn.FilterableColumn;
import com.broadcastemail.api.filterablecolumn.FilterableColumnRepository;
import com.broadcastemail.api.onboarding.OnboardingSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class ConnectionService {


    private final FilterableColumnRepository filterableColumnRepository;
    private ConnectionRepository connectionRepository;

    @Transactional
    public void createConnection(UUID accountId, OnboardingSession session,
                                 SchemaIntrospectionResult schema) {

        // save connection
        Connection connection = Connection.builder()
                .accountId(accountId)
                .name(session.getProjectRef())  // default name, user can rename in settings
                .type("supabase")
                .projectRef(session.getProjectRef())
                .projectUrl(session.getProjectUrl())
                .encryptedCreds(session.getEncryptedRolePassword())
                .userTableSchema(schema.userTableSchema())
                .userTableName(schema.userTableName())
                .emailColumn(schema.emailColumn())
                .userIdColumn(schema.userIdColumn())
                .build();

        connectionRepository.save(connection);

        // save filterable columns
        List<FilterableColumn> columns = schema.filterableColumns().stream()
                .map(col -> FilterableColumn.builder()
                        .connectionId(connection.getId())
                        .columnName(col.columnName())
                        .columnType(col.columnType())
                        .displayName(col.columnName()) // default display name
                        .enabled(col.enabled())
                        .cardinality(col.cardinality())
                        .cardinalityWarning(col.cardinalityWarning())
                        .build())
                .toList();

        filterableColumnRepository.saveAll(columns);
    }

}
