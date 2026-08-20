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


    public SchemaIntrospectionResult introspect(String jdbcUrl, String encryptedRolePassword) {
        String rolePassword = SecurityUtil.decrypt(encryptedRolePassword, encryptionProperties.key());
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "broadcastmail_reader", rolePassword);
             Statement stmt = connection.createStatement();) {

            // Find table with FK to auth.users.id
            ResultSet linkedRs = stmt.executeQuery(SupabaseSql.FIND_USER_LINKED_TABLES);
            if (!linkedRs.next()) {
                return new SchemaIntrospectionResult.NotDetected();
            }
            String linkedSchema = linkedRs.getString("table_schema");
            String linkedTable = linkedRs.getString("table_name");

            // Introspect that table's columns
            ResultSet rs = stmt.executeQuery(SupabaseSql.INTROSPECT_SCHEMA);
            Map<String, List<ColumnInfo>> tableColumns = new LinkedHashMap<>();
            while (rs.next()) {
                String key = rs.getString("table_schema") + "." + rs.getString("table_name");
                tableColumns.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new ColumnInfo(
                                rs.getString("column_name"),
                                rs.getString("data_type")
                        ));
            }

            List<DetectedColumn> filterableColumns = new ArrayList<>();
            List<ColumnInfo> profileColumns = tableColumns.get(linkedSchema + "." + linkedTable);
            if (profileColumns != null) {
                for (ColumnInfo col : profileColumns) {
                    if (isNonFilterable(col.name())) continue;
                    String uiType = mapToUiType(col.dataType());
                    int cardinality = getCardinality(connection, linkedTable, col.name());
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
                    linkedTable,
                    linkedSchema,
                    "email",
                    "id",
                    filterableColumns
            );

        } catch (SQLException e) {
            throw new RuntimeException("Schema introspection failed: " + e.getMessage(), e);
        }
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

    private int getCardinality(Connection conn, String tableName, String columnName) {
        if (!columnName.matches("[a-zA-Z_]\\w*")) {
            return 999;
        }
        if (!tableName.matches("[a-zA-Z_]\\w*")) {
            return 999;
        }

        try(PreparedStatement stmt = conn.prepareStatement(
                String.format("SELECT COUNT(DISTINCT \"%s\") FROM \"%s\"", columnName, tableName) //NOSONAR
        );) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException _) {
            // column might not be indexable — return high cardinality to be safe
            return 999;
        }
        return 0;
    }
    private record ColumnInfo(String name, String dataType) {}
        }