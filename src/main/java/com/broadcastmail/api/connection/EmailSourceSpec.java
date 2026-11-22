package com.broadcastmail.api.connection;

/** The table and column a connection reads recipients' emails from. */
public record EmailSourceSpec(String tableSchema, String tableName, String idColumn, String emailColumn) {}
