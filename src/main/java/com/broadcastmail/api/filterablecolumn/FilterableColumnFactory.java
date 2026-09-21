package com.broadcastmail.api.filterablecolumn;

import com.broadcastmail.api.connection.dto.DetectedColumn;
import com.broadcastmail.common.campaign.filter.FilterSource;

import java.util.List;
import java.util.UUID;

/** Builds {@link FilterableColumn} rows from introspected columns, tagged by where they came from. */
public final class FilterableColumnFactory {

    private FilterableColumnFactory() {}

    public static List<FilterableColumn> from(
            UUID connectionId, List<DetectedColumn> columns, FilterSource source, List<String> confirmedNames) {
        if (columns == null || confirmedNames == null) {
            return List.of();
        }
        return columns.stream()
                .filter(col -> confirmedNames.contains(col.columnName()))
                .map(col -> FilterableColumn.builder()
                        .connectionId(connectionId)
                        .columnName(col.columnName())
                        .columnType(col.columnType())
                        .displayName(col.columnName())
                        .source(source)
                        .enabled(true)
                        .cardinality(col.cardinality())
                        .cardinalityWarning(col.cardinalityWarning())
                        .build())
                .toList();
    }
}
