package com.broadcastmail.api.connection.dto;

import java.util.List;

public record FilterContextResponse(
        String status,
        UserTableInfo userTable,
        RecipientSourceInfo recipientSource,
        ColumnsInfo columns
) {
    public record UserTableInfo(String schema, String name, String idColumn) {}

    public record RecipientSourceInfo(String schema, String table, String emailColumn) {}

    public record ColumnsInfo(List<FilterColumnInfo> profile, List<FilterColumnInfo> auth) {}

    public record FilterColumnInfo(
            String name,
            String displayName,
            String dataType,
            boolean enabled,
            boolean recommended,
            String recommendationReason,
            Integer cardinality,
            boolean cardinalityWarning,
            List<String> allowedOperators,
            List<String> enumValues
    ) {}
}
