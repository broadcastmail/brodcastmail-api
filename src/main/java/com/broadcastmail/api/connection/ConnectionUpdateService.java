package com.broadcastmail.api.connection;

import com.broadcastmail.api.common.exceptions.ConnectionNotFoundException;
import com.broadcastmail.api.connection.dto.SchemaIntrospectionResult;
import com.broadcastmail.api.filterablecolumn.FilterableColumn;
import com.broadcastmail.api.filterablecolumn.FilterableColumnFactory;
import com.broadcastmail.api.filterablecolumn.FilterableColumnRepository;
import com.broadcastmail.api.supabase.SupabaseSql;
import com.broadcastmail.common.campaign.filter.FilterSource;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
        List<FilterableColumn> columns = new ArrayList<>();
        columns.addAll(FilterableColumnFactory.from(
                connection.getId(), detected.filterableColumns(), FilterSource.PROFILE_TABLE, columnNames));
        columns.addAll(FilterableColumnFactory.from(
                connection.getId(), detected.authColumns(), FilterSource.AUTH_METADATA, columnNames));
        filterableColumnRepository.saveAll(columns);
    }
}