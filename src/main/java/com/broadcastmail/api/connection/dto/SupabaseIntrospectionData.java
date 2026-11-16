package com.broadcastmail.api.connection.dto;

import java.util.List;
import java.util.Map;

public record SupabaseIntrospectionData(
        List<CandidateTable> candidateTables,
        Map<String, List<Column>> columnsByTable,
        Map<String, String> primaryKeys,
        Map<String, Map<String, Integer>> cardinalities,
        Map<String, List<String>> emailColumnsByTable
) {
    public record CandidateTable(String schema, String table, String idColumn) {}
    public record Column(String name, String dataType) {}
}
