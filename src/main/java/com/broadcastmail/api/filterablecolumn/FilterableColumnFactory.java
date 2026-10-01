package com.broadcastmail.api.filterablecolumn;

import com.broadcastmail.api.connection.dto.DetectedColumn;
import com.broadcastmail.common.campaign.filter.FilterSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Builds {@link FilterableColumn} rows from introspected columns, tagged by where they came from. */
public final class FilterableColumnFactory {

    private FilterableColumnFactory() {}

    public static List<DetectedColumn> applyPersisted(
            List<DetectedColumn> columns, List<FilterableColumn> persisted, FilterSource source) {
        if (columns == null || persisted.isEmpty()) {
            return columns;
        }
        Set<String> enabledNames = persisted.stream()
                .filter(p -> p.getSource() == source && Boolean.TRUE.equals(p.getEnabled()))
                .map(FilterableColumn::getColumnName)
                .collect(Collectors.toSet());
        return columns.stream()
                .map(col -> new DetectedColumn(col.columnName(), col.columnType(),
                        enabledNames.contains(col.columnName()), col.cardinality(), col.cardinalityWarning()))
                .toList();
    }

    public static List<FilterableColumn> from(
            UUID connectionId, List<DetectedColumn> columns, FilterSource source, List<String> confirmedNames) {
        if (columns == null || confirmedNames == null) {
            return List.of();
        }
        return columns.stream()
                .filter(col -> confirmedNames.contains(col.columnName()))
                .collect(Collectors.toMap(DetectedColumn::columnName, col -> col, (first, dup) -> first, LinkedHashMap::new))
                .values().stream()
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
