package com.broadcastmail.api.me.dto;

public record MeResponse(
        String email,
        String plan,
        String connectionName
) {}
