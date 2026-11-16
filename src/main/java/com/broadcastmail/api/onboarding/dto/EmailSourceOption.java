package com.broadcastmail.api.onboarding.dto;

import java.util.List;

public record EmailSourceOption(String tableSchema, String tableName, String primaryKeyColumn, List<String> emailColumnCandidates) {}
