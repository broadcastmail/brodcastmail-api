package com.broadcastmail.api.supabase.reader;

import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.supabase.SupabaseSql;
import com.broadcastmail.api.config.EncryptionProperties;
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

/**
 * Runs queries against a tenant project as its reader role, through Supavisor. One short-lived connection per call —
 * the pooler does the pooling.
 */
@Component
@RequiredArgsConstructor
public class TenantConnectionGateway {

    private static final int LOGIN_TIMEOUT_SECONDS = 3;

    private final EncryptionProperties encryptionProperties;

    public <T> T execute(Connection connection, ConnectionCallback<T> callback) throws SQLException {
        String password = SecurityUtil.decrypt(connection.getEncryptedCreds(), encryptionProperties.key());
        try (java.sql.Connection conn = connection.readerConnectionFactory().open(password, LOGIN_TIMEOUT_SECONDS)) {
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
        String source = new SupabaseSql.TableRef(schema, table).qualified();
        try {
            return execute(connection, conn -> {
                Map<String, List<String>> result = new LinkedHashMap<>();
                for (String column : columnNames) {
                    SupabaseSql.requireIdentifier(column, "column name");
                    String col = SupabaseSql.quoteIdent(column);
                    String sql = "SELECT DISTINCT " + col + " FROM " + source
                            + " WHERE " + col + " IS NOT NULL ORDER BY " + col + " LIMIT " + limit;
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
