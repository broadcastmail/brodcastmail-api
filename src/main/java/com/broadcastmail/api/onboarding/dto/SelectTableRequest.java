package com.broadcastmail.api.onboarding.dto;

public record SelectTableRequest(
        String userTableSchema,
        String userTableName
) {
    public boolean skipRequested() {
        return userTableSchema == null && userTableName == null;
    }
}
