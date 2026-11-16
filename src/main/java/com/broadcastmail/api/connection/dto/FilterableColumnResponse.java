package com.broadcastmail.api.connection.dto;

import com.broadcastmail.common.campaign.filter.FilterSource;
import com.broadcastmail.common.filterablecolumn.FilterableColumn;

public record FilterableColumnResponse(
        String columnName,
        String columnType,
        boolean enabled,
        Integer cardinality,
        boolean cardinalityWarning,
        FilterSource source
) {
    public static FilterableColumnResponse from(FilterableColumn col) {
        return new FilterableColumnResponse(
                col.getColumnName(),
                col.getColumnType(),
                col.getEnabled(),
                col.getCardinality(),
                col.getCardinalityWarning(),
                col.getSource()
        );
    }

}