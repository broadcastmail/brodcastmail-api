package com.broadcastmail.api.support;

import com.broadcastmail.api.supabase.management.ManagementSqlRunner;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Test-only {@link ManagementSqlRunner} over plain JDBC, standing in for the Management API against a local Supabase. */
public record JdbcSqlRunner(String jdbcUrl, String username, String password) implements ManagementSqlRunner {

    @Override
    public List<Map<String, Object>> query(String sql) {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) row.put(meta.getColumnLabel(i), rs.getObject(i));
                rows.add(row);
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
