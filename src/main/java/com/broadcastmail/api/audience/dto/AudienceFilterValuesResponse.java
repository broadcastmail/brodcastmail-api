package com.broadcastmail.api.audience.dto;

import java.util.List;

public record AudienceFilterValuesResponse(
        String column,
        List<FilterValue> values,
        String nextCursor,
        boolean hasMore
) {
    public record FilterValue(String value, long count) {}
}
