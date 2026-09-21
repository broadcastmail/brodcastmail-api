package com.broadcastmail.api.connection;

import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.connection.dto.DetectedColumn;
import com.broadcastmail.api.connection.dto.SchemaIntrospectionResult;
import com.broadcastmail.api.supabase.SupabaseSql;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SchemaIntrospectionService {

    private final EncryptionProperties encryptionProperties;

    /**
     * Finds every table with a FK to {@code auth.users.id} and fully introspects
     * each one. Zero matches → {@code NotDetected}; exactly one → {@code Detected}
     * for that table; more than one → {@code MultipleCandidates} so the caller can
     * let the user pick.
     */
    public SchemaIntrospectionResult introspect(String jdbcUrl, String encryptedRolePassword) {
        String rolePassword = SecurityUtil.decrypt(encryptedRolePassword, encryptionProperties.key());
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "broadcastmail_reader", rolePassword);
             Statement stmt = connection.createStatement();) {

            List<TableRef> candidates = findCandidateTables(stmt);
            if (candidates.isEmpty()) {
                return new SchemaIntrospectionResult.NotDetected();
            }

            Map<String, List<ColumnInfo>> tableColumns = loadAllColumns(stmt);
            List<DetectedColumn> authColumns = buildAuthColumns(connection, tableColumns);

            List<SchemaIntrospectionResult.Detected> detected = candidates.stream()
                    .map(ref -> buildDetected(connection, tableColumns, ref, authColumns))
                    .toList();

            return detected.size() == 1
                    ? detected.get(0)
                    : new SchemaIntrospectionResult.MultipleCandidates(detected);

        } catch (SQLException e) {
            throw new RuntimeException("Schema introspection failed: " + e.getMessage(), e);
        }
    }

    /**
     * Introspects one already-known table directly, without re-running FK
     * detection — for callers (e.g. reconnect) that already know which table
     * and FK column they want, rather than re-discovering candidates.
     */
    public SchemaIntrospectionResult.Detected introspectTable(
            String jdbcUrl, String encryptedRolePassword, String schema, String table, String idColumn) {
        String rolePassword = SecurityUtil.decrypt(encryptedRolePassword, encryptionProperties.key());
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "broadcastmail_reader", rolePassword);
             Statement stmt = connection.createStatement();) {

            Map<String, List<ColumnInfo>> tableColumns = loadAllColumns(stmt);
            List<DetectedColumn> authColumns = buildAuthColumns(connection, tableColumns);
            return buildDetected(connection, tableColumns, new TableRef(schema, table, idColumn), authColumns);

        } catch (SQLException e) {
            throw new RuntimeException("Schema introspection failed: " + e.getMessage(), e);
        }
    }

    private List<TableRef> findCandidateTables(Statement stmt) throws SQLException {
        List<TableRef> candidates = new ArrayList<>();
        try (ResultSet rs = stmt.executeQuery(SupabaseSql.FIND_USER_LINKED_TABLES)) {
            while (rs.next()) {
                candidates.add(new TableRef(
                        rs.getString("table_schema"),
                        rs.getString("table_name"),
                        rs.getString("column_name")
                ));
            }
        }
        return candidates;
    }

    private Map<String, List<ColumnInfo>> loadAllColumns(Statement stmt) throws SQLException {
        Map<String, List<ColumnInfo>> tableColumns = new LinkedHashMap<>();
        try (ResultSet rs = stmt.executeQuery(SupabaseSql.INTROSPECT_SCHEMA)) {
            while (rs.next()) {
                String key = rs.getString("table_schema") + "." + rs.getString("table_name");
                tableColumns.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new ColumnInfo(
                                rs.getString("column_name"),
                                rs.getString("data_type")
                        ));
            }
        }
        return tableColumns;
    }

    private SchemaIntrospectionResult.Detected buildDetected(
            Connection connection, Map<String, List<ColumnInfo>> tableColumns, TableRef ref,
            List<DetectedColumn> authColumns) {
        List<DetectedColumn> filterableColumns = new ArrayList<>();
        List<ColumnInfo> profileColumns = tableColumns.get(ref.schema() + "." + ref.table());
        if (profileColumns != null) {
            for (ColumnInfo col : profileColumns) {
                if (isNonFilterable(col.name())) continue;
                String uiType = mapToUiType(col.dataType());
                int cardinality = getCardinality(connection, ref.schema(), ref.table(), col.name());
                boolean warning = cardinality > 50;
                filterableColumns.add(new DetectedColumn(
                        col.name(),
                        uiType,
                        true,
                        cardinality,
                        warning
                ));
            }
        }

        filterableColumns.add(new DetectedColumn(
                "created_at",
                "timestamptz",
                true,
                0,
                false
        ));

        return new SchemaIntrospectionResult.Detected(
                ref.table(),
                ref.schema(),
                ref.idColumn(),
                filterableColumns,
                authColumns
        );
    }

    /**
     * Flat auth.users metadata columns available for filtering, sourced from the
     * already-widened auth.user_emails view (see SupabaseSql.CREATE_READER_ROLE),
     * restricted to the explicit safe allow-list — never every auth.users column.
     * The same for every connection, so computed once per introspection call rather
     * than per candidate table.
     */
    private List<DetectedColumn> buildAuthColumns(Connection connection, Map<String, List<ColumnInfo>> tableColumns) {
        List<ColumnInfo> authUserColumns = tableColumns.get("auth.users");
        if (authUserColumns == null) {
            return List.of();
        }
        List<DetectedColumn> authColumns = new ArrayList<>();
        for (ColumnInfo col : authUserColumns) {
            if (!SupabaseSql.AUTH_METADATA_COLUMNS.contains(col.name())) continue;
            String uiType = mapToUiType(col.dataType());
            int cardinality = getCardinality(connection, "auth", "user_emails", col.name());
            authColumns.add(new DetectedColumn(col.name(), uiType, true, cardinality, cardinality > 50));
        }
        return authColumns;
    }

    private boolean isNonFilterable(String columnName) {
        return columnName.equals("id") ||
                columnName.equals("email") ||
                columnName.endsWith("_url") ||
                columnName.endsWith("_token") ||
                columnName.endsWith("_hash");
    }

    private String mapToUiType(String postgresType) {
        return switch (postgresType.toLowerCase()) {
            case "boolean" -> "boolean";
            case "integer", "bigint", "numeric", "real", "double precision" -> "integer";
            case "timestamp with time zone", "timestamp without time zone", "date" -> "timestamptz";
            default -> "text";
        };
    }

    private static final String IDENTIFIER_PATTERN = "[a-zA-Z_]\\w*";

    private int getCardinality(Connection conn, String schemaName, String tableName, String columnName) {
        if (!columnName.matches(IDENTIFIER_PATTERN)) {
            return 999;
        }
        if (!tableName.matches(IDENTIFIER_PATTERN) || !schemaName.matches(IDENTIFIER_PATTERN)) {
            return 999;
        }

        try(PreparedStatement stmt = conn.prepareStatement(
                String.format("SELECT COUNT(DISTINCT \"%s\") FROM \"%s\".\"%s\"", columnName, schemaName, tableName) //NOSONAR
        );) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException _) {
            // column might not be indexable — return high cardinality to be safe
            return 999;
        }
        return 0;
    }

    private record TableRef(String schema, String table, String idColumn) {}
    private record ColumnInfo(String name, String dataType) {}
}
