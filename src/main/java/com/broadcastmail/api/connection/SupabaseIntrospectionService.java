package com.broadcastmail.api.connection;

import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.connection.dto.SupabaseIntrospectionData;
import com.broadcastmail.api.supabase.SupabaseSql;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.sql.Connection;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SupabaseIntrospectionService {

    private static final String COL_COLUMN_NAME = "column_name";

    private final EncryptionProperties encryptionProperties;

    public SupabaseIntrospectionData introspect(String jdbcUrl, String encryptedRolePassword) {
        String rolePassword = SecurityUtil.decrypt(encryptedRolePassword, encryptionProperties.key());
        try (Connection conn = DriverManager.getConnection(jdbcUrl, SupabaseSql.READER_ROLE, rolePassword);
             Statement stmt = conn.createStatement()) {

            List<SupabaseIntrospectionData.CandidateTable> candidates = findCandidateTables(stmt);
            Map<String, List<SupabaseIntrospectionData.Column>> columnsByTable = loadAllColumns(stmt);
            Map<String, String> primaryKeys = extractPrimaryKeys(stmt);
            Map<String, Map<String, Integer>> cardinalities = computeCardinalities(conn, candidates, columnsByTable);
            Map<String, List<String>> emailColumnsByTable = detectEmailColumns(conn, columnsByTable);

            return new SupabaseIntrospectionData(candidates, columnsByTable, primaryKeys, cardinalities, emailColumnsByTable);
        } catch (SQLException e) {
            throw new RuntimeException("Supabase introspection failed: " + e.getMessage(), e);
        }
    }

    public SupabaseIntrospectionData introspectSingleTable(
            String jdbcUrl, String encryptedRolePassword, String schema, String table, String idColumn) {
        String rolePassword = SecurityUtil.decrypt(encryptedRolePassword, encryptionProperties.key());
        try (Connection conn = DriverManager.getConnection(jdbcUrl, SupabaseSql.READER_ROLE, rolePassword);
             Statement stmt = conn.createStatement()) {

            List<SupabaseIntrospectionData.CandidateTable> candidates =
                    List.of(new SupabaseIntrospectionData.CandidateTable(schema, table, idColumn));
            Map<String, List<SupabaseIntrospectionData.Column>> columnsByTable = loadAllColumns(stmt);
            Map<String, String> primaryKeys = extractPrimaryKeys(stmt);
            Map<String, Map<String, Integer>> cardinalities = computeCardinalities(conn, candidates, columnsByTable);
            Map<String, List<String>> emailColumnsByTable = detectEmailColumns(conn, columnsByTable);

            return new SupabaseIntrospectionData(candidates, columnsByTable, primaryKeys, cardinalities, emailColumnsByTable);
        } catch (SQLException e) {
            throw new RuntimeException("Supabase introspection failed: " + e.getMessage(), e);
        }
    }

    public String detectPrimaryKey(String jdbcUrl, String encryptedRolePassword, String schema, String table) {
        String rolePassword = SecurityUtil.decrypt(encryptedRolePassword, encryptionProperties.key());
        try (Connection conn = DriverManager.getConnection(jdbcUrl, SupabaseSql.READER_ROLE, rolePassword);
             PreparedStatement stmt = conn.prepareStatement(SupabaseSql.FIND_TABLE_PRIMARY_KEY)) {
            stmt.setString(1, schema);
            stmt.setString(2, table);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString(1);
            throw new IllegalStateException("No primary key found for " + schema + "." + table);
        } catch (SQLException e) {
            throw new IllegalStateException("Primary key detection failed: " + e.getMessage(), e);
        }
    }

    private List<SupabaseIntrospectionData.CandidateTable> findCandidateTables(Statement stmt) throws SQLException {
        List<SupabaseIntrospectionData.CandidateTable> candidates = new ArrayList<>();
        try (ResultSet rs = stmt.executeQuery(SupabaseSql.FIND_USER_LINKED_TABLES)) {
            while (rs.next()) {
                candidates.add(new SupabaseIntrospectionData.CandidateTable(
                        rs.getString("table_schema"),
                        rs.getString("table_name"),
                        rs.getString(COL_COLUMN_NAME)
                ));
            }
        }
        return candidates;
    }

    private Map<String, List<SupabaseIntrospectionData.Column>> loadAllColumns(Statement stmt) throws SQLException {
        Map<String, List<SupabaseIntrospectionData.Column>> columnsByTable = new LinkedHashMap<>();
        try (ResultSet rs = stmt.executeQuery(SupabaseSql.INTROSPECT_SCHEMA)) {
            while (rs.next()) {
                String key = rs.getString("table_schema") + "." + rs.getString("table_name");
                columnsByTable.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new SupabaseIntrospectionData.Column(
                                rs.getString(COL_COLUMN_NAME),
                                rs.getString("data_type")
                        ));
            }
        }
        return columnsByTable;
    }

    private Map<String, String> extractPrimaryKeys(Statement stmt) throws SQLException {
        Map<String, String> result = new HashMap<>();
        try (ResultSet rs = stmt.executeQuery(SupabaseSql.FIND_ALL_PRIMARY_KEYS)) {
            while (rs.next()) {
                result.putIfAbsent(rs.getString("table_key"), rs.getString(COL_COLUMN_NAME));
            }
        }
        return result;
    }

    private Map<String, Map<String, Integer>> computeCardinalities(
            Connection conn,
            List<SupabaseIntrospectionData.CandidateTable> candidates,
            Map<String, List<SupabaseIntrospectionData.Column>> columnsByTable) {
        Map<String, Map<String, Integer>> cardinalities = new HashMap<>();

        for (SupabaseIntrospectionData.CandidateTable candidate : candidates) {
            String key = candidate.schema() + "." + candidate.table();
            List<SupabaseIntrospectionData.Column> columns = columnsByTable.get(key);
            if (columns == null) continue;
            Map<String, Integer> tableCards = new HashMap<>();
            for (SupabaseIntrospectionData.Column col : columns) {
                tableCards.put(col.name(), getCardinality(conn, candidate.schema(), candidate.table(), col.name()));
            }
            cardinalities.put(key, tableCards);
        }

        List<SupabaseIntrospectionData.Column> authCols = columnsByTable.get("auth.users");
        if (authCols != null) {
            Map<String, Integer> authCards = new HashMap<>();
            for (SupabaseIntrospectionData.Column col : authCols) {
                if (SupabaseSql.AUTH_METADATA_COLUMNS.contains(col.name())) {
                    authCards.put(col.name(), getCardinality(conn, "auth", "user_emails", col.name()));
                }
            }
            cardinalities.put("auth.user_emails", authCards);
        }

        return cardinalities;
    }

    private Map<String, List<String>> detectEmailColumns(
            Connection conn, Map<String, List<SupabaseIntrospectionData.Column>> columnsByTable) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        columnsByTable.forEach((key, columns) -> {
            if (key.startsWith("auth.")) return;
            int dot = key.indexOf('.');
            String schema = key.substring(0, dot);
            String table = key.substring(dot + 1);
            List<String> emailCols = columns.stream()
                    .filter(c -> c.dataType().equals("text") || c.dataType().startsWith("character varying"))
                    .map(SupabaseIntrospectionData.Column::name)
                    .filter(name -> isLikelyEmailColumn(conn, schema, table, name))
                    .toList();
            if (!emailCols.isEmpty()) result.put(key, emailCols);
        });
        return result;
    }

    private boolean isLikelyEmailColumn(Connection conn, String schema, String table, String column) {
        if (!SupabaseSql.IDENTIFIER_PATTERN.matcher(column).matches()) return false;
        if (!SupabaseSql.IDENTIFIER_PATTERN.matcher(table).matches()
                || !SupabaseSql.IDENTIFIER_PATTERN.matcher(schema).matches()) return false;
        try (PreparedStatement stmt = conn.prepareStatement(
                String.format(SupabaseSql.CHECK_EMAIL_COLUMN, column, column, schema, table, column))) { //NOSONAR java:S2077
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getBoolean(1);
        } catch (SQLException _) {
            return false;
        }
    }

    private int getCardinality(Connection conn, String schemaName, String tableName, String columnName) {
        if (!SupabaseSql.IDENTIFIER_PATTERN.matcher(columnName).matches()) return 999;
        if (!SupabaseSql.IDENTIFIER_PATTERN.matcher(tableName).matches()
                || !SupabaseSql.IDENTIFIER_PATTERN.matcher(schemaName).matches()) return 999;
        try (PreparedStatement stmt = conn.prepareStatement(
                String.format(SupabaseSql.COUNT_DISTINCT_COLUMN, columnName, schemaName, tableName))) { //NOSONAR java:S2077
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException _) {
            return 999;
        }
        return 0;
    }
}
