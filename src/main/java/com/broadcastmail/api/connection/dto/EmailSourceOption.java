package com.broadcastmail.api.connection.dto;

import java.util.List;

public record EmailSourceOption(String tableSchema, String tableName, String primaryKeyColumn, List<String> emailColumnCandidates) {}
