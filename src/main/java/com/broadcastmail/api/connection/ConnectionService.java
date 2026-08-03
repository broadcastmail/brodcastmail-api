package com.broadcastmail.api.connection;

import com.broadcastmail.api.filterablecolumn.FilterableColumn;
import com.broadcastmail.api.filterablecolumn.FilterableColumnRepository;
import com.broadcastmail.api.onboarding.OnboardingSession;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ConnectionService {

    private final ConnectionRepository connectionRepository;
    private final FilterableColumnRepository filterableColumnRepository;

    public void createConnection(UUID accountId, OnboardingSession session) {
        Connection connection = Connection.builder()
                .accountId(accountId)
                .projectRef(session.getProjectRef())
                .projectUrl(session.getProjectUrl())
                .encryptedCreds(session.getEncryptedRolePassword())
                .userTableSchema(session.getSchemaDetails().userSchema())
                .userTableName(session.getSchemaDetails().userTable())
                .emailColumn("email")
                .userIdColumn("id")
                .build();
        connectionRepository.save(connection);

        if (session.getDetectedColumns() != null && session.getConfirmedColumnNames() != null) {
            List<FilterableColumn> filterableColumns = session.getDetectedColumns().stream()
                    .filter(col -> session.getConfirmedColumnNames().contains(col.columnName()))
                    .map(col -> FilterableColumn.builder()
                            .connectionId(connection.getId())
                            .columnName(col.columnName())
                            .columnType(col.columnType())
                            .displayName(col.columnName())
                            .cardinality(col.cardinality())
                            .cardinalityWarning(col.cardinalityWarning())
                            .build())
                    .toList();
            filterableColumnRepository.saveAll(filterableColumns);
        }
    }
}
