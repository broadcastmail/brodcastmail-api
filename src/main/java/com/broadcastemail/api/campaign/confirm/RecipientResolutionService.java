package com.broadcastemail.api.campaign.confirm;

import com.broadcastemail.api.campaign.confirm.dto.RecipientRow;
import com.broadcastemail.api.campaign.filter.FilterQuery;
import com.broadcastemail.api.connection.Connection;
import org.springframework.stereotype.Service;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Service
public class RecipientResolutionService {

    public List<RecipientRow> resolve(String jdbcUrl, String rolePassword, Connection connection, FilterQuery filterQuery) {        validateColumnName(connection.getUserIdColumn());
        validateColumnName(connection.getEmailColumn());

        String base;
        if (filterQuery.sql().isBlank()) {
            base = "SELECT u." + connection.getUserIdColumn() + ", u." + connection.getEmailColumn()
                    + " FROM auth.user_emails u";
        } else {
            base = "SELECT u." + connection.getUserIdColumn() + ", u." + connection.getEmailColumn()
                    + " FROM auth.user_emails u"
                    + " JOIN " + connection.getUserTableSchema() + "." + connection.getUserTableName()
                    + " p ON p." + connection.getUserIdColumn() + " = u." + connection.getUserIdColumn();
        }
        String sql = filterQuery.sql().isBlank() ? base : base + " " + filterQuery.sql();


        // TODO: CWE-316 — accept char[] and zero out after DriverManager.getConnection()
        try (java.sql.Connection conn = DriverManager.getConnection(jdbcUrl, "broadcastmail_reader", rolePassword);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < filterQuery.parameters().size(); i++) {
                stmt.setObject(i + 1, filterQuery.parameters().get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                List<RecipientRow> recipients = new ArrayList<>();
                while (rs.next()) {
                    recipients.add(new RecipientRow(
                            rs.getString(connection.getUserIdColumn()),
                            rs.getString(connection.getEmailColumn())
                    ));
                }
                return recipients;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to resolve recipients: " + e.getMessage(), e);
        }
    }

    private void validateColumnName(String name) {
        if (!name.matches("[a-zA-Z_]\\w*")) {
            throw new IllegalArgumentException("Invalid column name: " + name);
        }
    }
}
