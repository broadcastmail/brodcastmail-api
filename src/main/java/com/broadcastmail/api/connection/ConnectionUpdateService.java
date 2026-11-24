package com.broadcastmail.api.connection;

import com.broadcastmail.api.connection.schema.EmailSourceService;
import com.broadcastmail.api.connection.schema.SchemaIntrospectionService;
import com.broadcastmail.api.common.exceptions.ConnectionNotFoundException;
import com.broadcastmail.api.connection.dto.SchemaIntrospectionResult;
import com.broadcastmail.api.filterablecolumn.FilterableColumnFactory;
import com.broadcastmail.api.supabase.reader.ReaderConnectionVerifier;
import com.broadcastmail.api.supabase.reader.ReaderRoleService;
import com.broadcastmail.api.supabase.management.ManagementSqlRunner;
import com.broadcastmail.api.supabase.management.ProjectNameResolver;
import com.broadcastmail.api.supabase.management.SupabaseAccessTokens;
import com.broadcastmail.api.supabase.management.SupabaseManagementClient;
import com.broadcastmail.api.supabase.SupabaseSql;
import com.broadcastmail.common.filterablecolumn.FilterableColumn;
import com.broadcastmail.common.filterablecolumn.FilterableColumnRepository;
import com.broadcastmail.common.campaign.filter.FilterSource;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import com.broadcastmail.common.connection.ReaderConnectionFactory;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ConnectionUpdateService {

    private final ConnectionRepository connectionRepository;
    private final FilterableColumnRepository filterableColumnRepository;
    private final SchemaIntrospectionService schemaIntrospectionService;
    private final EmailSourceService emailSourceService;
    private final SupabaseManagementClient supabaseManagementClient;
    private final SupabaseAccessTokens supabaseAccessTokens;
    private final ReaderRoleService readerRoleService;
    private final ReaderConnectionVerifier readerConnectionVerifier;
    private final ProjectNameResolver projectNameResolver;

    public void updateProject(UUID accountId, String projectRef) {
        Connection connection = findConnection(accountId);
        String accessToken = supabaseAccessTokens.forAccount(accountId);

        if (projectRef.equals(connection.getProjectRef())) {
            // Tables are cleared below — drop their grants too.
            readerRoleService.syncGrants(accessToken, projectRef, connection.getDbRole(), Set.of());
        } else {
            moveToProject(connection, accessToken, projectRef);
            connection.setName(projectNameResolver.resolve(projectRef, () -> accessToken));
        }

        connection.setUserTableName(null);
        connection.setUserTableSchema(null);
        connection.setUserIdColumn(null);
        connection.clearEmailTable();
        connectionRepository.save(connection);
        filterableColumnRepository.deleteByConnectionId(connection.getId());
    }

    public void updateSchemaTable(UUID accountId, String userTableSchema, String userTableName, String userIdColumn) {
        Connection connection = findConnection(accountId);
        connection.setUserTableSchema(userTableSchema);
        connection.setUserTableName(userTableName);
        connection.setUserIdColumn(userIdColumn);
        syncGrants(accountId, connection);
        connectionRepository.save(connection);
        filterableColumnRepository.deleteByConnectionId(connection.getId());
    }

    public void updateEmailTable(UUID accountId, String emailTableSchema, String emailTableName, String emailColumn) {
        Connection connection = findConnection(accountId);
        String pk = emailSourceService.detectPrimaryKey(sqlRunner(accountId, connection), emailTableSchema, emailTableName);
        connection.setEmailTableSchema(emailTableSchema);
        connection.setEmailTableName(emailTableName);
        connection.setEmailTableIdColumn(pk);
        connection.setEmailColumn(emailColumn);
        syncGrants(accountId, connection);
        connectionRepository.save(connection);
    }

    public void deleteProfileColumns(UUID accountId) {
        Connection connection = findConnection(accountId);
        filterableColumnRepository.deleteByConnectionIdAndSource(connection.getId(), FilterSource.PROFILE_TABLE);
    }

    public void updateSchemaColumns(UUID accountId, List<String> columnNames) {
        Connection connection = findConnection(accountId);

        SchemaIntrospectionResult.Detected detected =
                schemaIntrospectionService.introspectSaved(sqlRunner(accountId, connection), connection);

        filterableColumnRepository.deleteByConnectionId(connection.getId());
        List<FilterableColumn> columns = new ArrayList<>();
        columns.addAll(FilterableColumnFactory.from(
                connection.getId(), detected.filterableColumns(), FilterSource.PROFILE_TABLE, columnNames));
        columns.addAll(FilterableColumnFactory.from(
                connection.getId(), detected.authColumns(), FilterSource.AUTH_METADATA, columnNames));
        filterableColumnRepository.saveAll(columns);
    }

    /**
     * New project → new role there (the old role doesn't exist on it). Verified before switching so the connection
     * never points at a role the pooler rejects; the old project's role is dropped best effort.
     */
    private void moveToProject(Connection connection, String accessToken, String projectRef) {
        String poolerPrefix = supabaseManagementClient.getPoolerPrefix(accessToken, projectRef);
        ReaderRoleService.Provisioned provisioned = readerRoleService.provision(accessToken, projectRef, Set.of());
        try {
            readerConnectionVerifier.verify(
                    new ReaderConnectionFactory(projectRef, poolerPrefix, provisioned.role()),
                    readerRoleService.decryptPassword(provisioned.encryptedPassword()));
        } catch (RuntimeException e) {
            readerRoleService.dropQuietly(accessToken, projectRef, provisioned.role());
            throw e;
        }
        readerRoleService.dropQuietly(accessToken, connection.getProjectRef(), connection.getDbRole());

        connection.setProjectRef(projectRef);
        connection.setProjectUrl(SupabaseSql.buildProjectUrl(projectRef));
        connection.setPoolerPrefix(poolerPrefix);
        connection.setDbRole(provisioned.role());
        connection.setEncryptedCreds(provisioned.encryptedPassword());
    }

    private void syncGrants(UUID accountId, Connection connection) {
        readerRoleService.syncGrants(supabaseAccessTokens.forAccount(accountId), connection.getProjectRef(),
                connection.getDbRole(), ConnectionService.tablesOf(connection));
    }

    private ManagementSqlRunner sqlRunner(UUID accountId, Connection connection) {
        return supabaseManagementClient.sqlRunner(supabaseAccessTokens.forAccount(accountId), connection.getProjectRef());
    }

    private Connection findConnection(UUID accountId) {
        return connectionRepository.findByAccountId(accountId).orElseThrow(ConnectionNotFoundException::new);
    }
}
