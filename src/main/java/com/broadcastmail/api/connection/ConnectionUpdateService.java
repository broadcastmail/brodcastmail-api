package com.broadcastmail.api.connection;

import com.broadcastmail.api.common.exceptions.ConnectionNotFoundException;
import com.broadcastmail.api.connection.dto.SchemaIntrospectionResult;
import com.broadcastmail.api.filterablecolumn.FilterableColumn;
import com.broadcastmail.api.filterablecolumn.FilterableColumnRepository;
import com.broadcastmail.api.supabase.SupabaseSql;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ConnectionUpdateService {

    private final ConnectionRepository connectionRepository;
    private final FilterableColumnRepository filterableColumnRepository;
    private final SchemaIntrospectionService schemaIntrospectionService;

    public void updateProject(UUID accountId, String projectRef) {
        Connection connection = connectionRepository.findByAccountId(accountId)
                .orElseThrow(ConnectionNotFoundException::new);
        connection.setProjectRef(projectRef);
        connection.setUserTableName(null);
        connection.setUserTableSchema(null);
        connection.setEmailColumn(null);
        connection.setUserIdColumn(null);
        connectionRepository.save(connection);
        filterableColumnRepository.deleteByConnectionId(connection.getId());
    }

    public void updateTable(UUID accountId, String userTableSchema, String userTableName, String userIdColumn) {
        Connection connection = connectionRepository.findByAccountId(accountId)
                .orElseThrow(ConnectionNotFoundException::new);
        connection.setUserTableSchema(userTableSchema);
        connection.setUserTableName(userTableName);
        connection.setEmailColumn(null);
        connection.setUserIdColumn(userIdColumn);
        connectionRepository.save(connection);
        filterableColumnRepository.deleteByConnectionId(connection.getId());
    }

    public void updateColumns(UUID accountId, List<String> columnNames) {
        Connection connection = connectionRepository.findByAccountId(accountId)
                .orElseThrow(ConnectionNotFoundException::new);

        SchemaIntrospectionResult.Detected detected = schemaIntrospectionService.introspectTable(
                SupabaseSql.buildJdbcUrl(connection.getProjectRef()),
                connection.getEncryptedCreds(),
                connection.getUserTableSchema(),
                connection.getUserTableName(),
                connection.getUserIdColumn()
        );

        filterableColumnRepository.deleteByConnectionId(connection.getId());
        List<FilterableColumn> columns = detected.filterableColumns().stream()
                .filter(col -> columnNames.contains(col.columnName()))
                .map(col -> FilterableColumn.builder()
                        .connectionId(connection.getId())
                        .columnName(col.columnName())
                        .columnType(col.columnType())
                        .displayName(col.columnName())
                        .enabled(true)
                        .cardinalityWarning(col.cardinalityWarning())
                        .cardinality(col.cardinality())
                        .build())
                .toList();
        filterableColumnRepository.saveAll(columns);
    }
}