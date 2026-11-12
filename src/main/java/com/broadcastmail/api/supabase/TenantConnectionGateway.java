package com.broadcastmail.api.supabase;

import com.broadcastmail.common.connection.Connection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TenantConnectionGateway {

    private final TenantDataSourceFactory dataSourceFactory;

    public <T> T execute(Connection connection, ConnectionCallback<T> callback) throws SQLException {
        try (java.sql.Connection conn = dataSourceFactory.create(connection).getConnection()) {
            return callback.execute(conn);
        }
    }

    //NOSONAR java:S2077 column names come from our own schema introspection, not user input
    @SuppressWarnings("java:S2077")
    public Map<String, List<String>> fetchDistinctColumnValues(Connection connection,
                                                               List<String> columnNames,
                                                               String schema, String table,
                                                               int limit) {
        if (columnNames.isEmpty()) return Map.of();
        try {
            return execute(connection, conn -> {
                Map<String, List<String>> result = new LinkedHashMap<>();
                for (String column : columnNames) {
                    SupabaseSql.requireIdentifier(column, "column name");
                    String sql = String.format(
                            "SELECT DISTINCT \"%s\" FROM \"%s\".\"%s\" WHERE \"%s\" IS NOT NULL ORDER BY \"%s\" LIMIT %d",
                            column, schema, table, column, column, limit);
                    try (PreparedStatement stmt = conn.prepareStatement(sql);
                         ResultSet rs = stmt.executeQuery()) {
                        List<String> values = new ArrayList<>();
                        while (rs.next()) values.add(rs.getString(1));
                        if (!values.isEmpty()) result.put(column, values);
                    }
                }
                return result;
            });
        } catch (SQLException _) {
            return Map.of();
        }
    }

    @FunctionalInterface
    public interface ConnectionCallback<T> {
        T execute(java.sql.Connection conn) throws SQLException;
    }
}
