package com.broadcastemail.api.connection.dto;

public record DetectedColumn(
        String columnName,
        String columnType,
        boolean enabled,
        Integer cardinality,
        boolean cardinalityWarning) {
}
