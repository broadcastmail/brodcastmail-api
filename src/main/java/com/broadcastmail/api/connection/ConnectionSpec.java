package com.broadcastmail.api.connection;

public record ConnectionSpec(
        String projectRef,
        String projectUrl,
        String poolerPrefix,
        String dbRole,
        String encryptedRolePassword,
        String userTableSchema,
        String userTableName,
        String userIdColumn
) {}
