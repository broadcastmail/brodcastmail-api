package com.broadcastmail.api.connection.dto;

import java.util.List;

public record ReconnectCompatibilityResponse(
        boolean userTableOk,
        boolean emailColumnOk,
        List<String> missingFilterColumns
) {}
