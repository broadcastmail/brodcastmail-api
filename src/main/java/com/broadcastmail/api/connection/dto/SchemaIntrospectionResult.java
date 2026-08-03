package com.broadcastmail.api.connection.dto;

import java.util.List;

public record SchemaIntrospectionResult(
        String userTableName,
        String userTableSchema,
        String emailColumn,
        String userIdColumn,
        List<DetectedColumn> filterableColumns
) {
}
