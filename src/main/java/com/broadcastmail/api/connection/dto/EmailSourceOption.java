package com.broadcastmail.api.connection.dto;

import java.util.List;

public record EmailSourceOption(String tableSchema, String tableName, String primaryKeyColumn, List<String> emailColumnCandidates) {

    // The sign-up address. Stored as no email table: the connection then reads _batchbeam.user_emails.
    public static final EmailSourceOption AUTH_USERS = new EmailSourceOption("auth", "users", "id", List.of("email"));

    public static boolean isAuthUsers(String schema, String table) {
        return AUTH_USERS.tableSchema().equals(schema) && AUTH_USERS.tableName().equals(table);
    }
}
