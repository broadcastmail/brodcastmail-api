package com.broadcastemail.api.onboarding.dto;

import java.util.List;

public record SchemaConfirmRequest(
        List<String> columnNames
) {
}
