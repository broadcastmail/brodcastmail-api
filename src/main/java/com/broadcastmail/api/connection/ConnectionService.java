package com.broadcastmail.api.connection;

import com.broadcastmail.api.common.exceptions.ConnectionNotFoundException;
import com.broadcastmail.api.filterablecolumn.ColumnSelection;
import com.broadcastmail.api.filterablecolumn.FilterableColumnService;
import com.broadcastmail.api.supabase.reader.ReaderRoleService;
import com.broadcastmail.api.supabase.SupabaseSql.TableRef;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ConnectionService {

    private final ConnectionRepository connectionRepository;
    private final FilterableColumnService filterableColumnService;

    public Connection getByAccountId(UUID accountId) {
        return connectionRepository.findByAccountId(accountId).orElseThrow(ConnectionNotFoundException::new);
    }

    public void createConnection(UUID accountId, ConnectionSpec spec, EmailSourceSpec emailSource, ColumnSelection columns) {
        Connection connection = Connection.builder()
                .accountId(accountId)
                .name(spec.projectName() != null && !spec.projectName().isBlank() ? spec.projectName() : spec.projectRef())
                .type("supabase")
                .emailTableSchema(emailSource.tableSchema())
                .emailTableName(emailSource.tableName())
                .emailTableIdColumn(emailSource.idColumn())
                .emailColumn(emailSource.emailColumn())
                .build();
        apply(connection, spec);
        connectionRepository.save(connection);
        filterableColumnService.create(connection.getId(), columns);
    }

    /** Points an existing connection at a new project/role/profile table; the email table is kept. */
    public Connection reconfigure(Connection connection, ConnectionSpec spec, ColumnSelection columns) {
        apply(connection, spec);
        connectionRepository.save(connection);
        filterableColumnService.replace(connection.getId(), columns);
        return connection;
    }

    /** The tables the connection's reader role must be able to read: its profile table and email table. */
    public static Set<TableRef> tablesOf(Connection connection) {
        return ReaderRoleService.tables(
                connection.getUserTableSchema(), connection.getUserTableName(),
                connection.getEmailTableSchema(), connection.getEmailTableName());
    }

    private static void apply(Connection connection, ConnectionSpec spec) {
        if (spec.projectName() != null && !spec.projectName().isBlank()) {
            connection.setName(spec.projectName());
        }
        connection.setProjectRef(spec.projectRef());
        connection.setProjectUrl(spec.projectUrl());
        connection.setPoolerPrefix(spec.poolerPrefix());
        connection.setDbRole(spec.dbRole());
        connection.setEncryptedCreds(spec.encryptedRolePassword());
        connection.setUserTableSchema(spec.userTableSchema());
        connection.setUserTableName(spec.userTableName());
        connection.setUserIdColumn(spec.userIdColumn());
    }
}
