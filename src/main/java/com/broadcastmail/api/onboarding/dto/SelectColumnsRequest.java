package com.broadcastmail.api.onboarding.dto;

import java.util.List;

public record SelectColumnsRequest(
        List<String> columnNames
) {
}
