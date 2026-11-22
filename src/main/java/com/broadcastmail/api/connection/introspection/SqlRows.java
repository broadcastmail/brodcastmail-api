package com.broadcastmail.api.connection.introspection;

import com.broadcastmail.api.supabase.SupabaseSql;

import java.util.Map;

/** Reads values out of {@link com.broadcastmail.api.supabase.management.ManagementSqlRunner} rows, whose JSON types vary by source. */
final class SqlRows {

    private SqlRows() {}

    static String string(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    /** Management API returns numbers as JSON numbers (sometimes strings); booleans count as 1/0. */
    static Long number(Object value) {
        return switch (value) {
            case null -> null;
            case Number n -> n.longValue();
            case Boolean b -> Boolean.TRUE.equals(b) ? 1L : 0L;
            default -> Long.parseLong(value.toString());
        };
    }

    static boolean isIdentifier(String value) {
        return value != null && SupabaseSql.IDENTIFIER_PATTERN.matcher(value).matches();
    }
}
