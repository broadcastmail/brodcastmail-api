package com.broadcastmail.api.audience;

import com.broadcastmail.api.audience.dto.AudienceListResponse;
import com.broadcastmail.api.supabase.TenantConnectionGateway;
import com.broadcastmail.common.connection.Connection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class TenantAudienceGateway {

    private final TenantConnectionGateway connectionGateway;

    public record AudienceQueryResult(long total, List<AudienceListResponse.AudienceRecipient> recipients) {}

    public int countRecipients(Connection connection, String sql,
                               List<Object> filterParams, Set<String> excluded) throws SQLException {
        return connectionGateway.execute(connection, conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                int paramIndex = 1;
                for (Object param : filterParams) {
                    stmt.setObject(paramIndex++, param);
                }
                if (!excluded.isEmpty()) {
                    Array excludedArray = conn.createArrayOf("text", excluded.toArray());
                    stmt.setArray(paramIndex, excludedArray);
                }
                ResultSet rs = stmt.executeQuery();
                return rs.next() ? rs.getInt(1) : 0;
            }
        });
    }

    public AudienceQueryResult queryAudience(Connection connection, String sql,
                                             List<Object> filterParams, List<Object> pageParams,
                                             int limit) throws SQLException {
        return connectionGateway.execute(connection, conn -> {
            List<Object> params = Stream.concat(filterParams.stream(), pageParams.stream())
                    .collect(Collectors.toCollection(ArrayList::new));
            params.add(limit);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.size(); i++) stmt.setObject(i + 1, params.get(i));
                ResultSet rs = stmt.executeQuery();
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                List<AudienceListResponse.AudienceRecipient> recipients = new ArrayList<>();
                long total = 0;
                while (rs.next()) {
                    if (recipients.isEmpty()) total = rs.getLong("__total");
                    UUID id = UUID.fromString(rs.getString("__id"));
                    String email = rs.getString("__email");
                    Map<String, Object> attributes = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        String label = meta.getColumnLabel(i);
                        if (label.equals("__id") || label.equals("__email") || label.equals("__total")) continue;
                        attributes.put(label, rs.getObject(i));
                    }
                    recipients.add(new AudienceListResponse.AudienceRecipient(id, email, attributes, false));
                }
                return new AudienceQueryResult(total, recipients);
            }
        });
    }
}
