package com.broadcastmail.api.onboarding.dto;

public record ConfirmEmailTableRequest(String emailTableSchema, String emailTableName, String primaryKeyColumn, String emailColumn) {}
