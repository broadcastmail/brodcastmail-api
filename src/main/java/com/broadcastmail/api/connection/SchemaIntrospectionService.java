package com.broadcastmail.api.connection;

import com.broadcastmail.api.connection.dto.DetectedColumn;
import com.broadcastmail.api.connection.dto.SchemaIntrospectionResult;
import com.broadcastmail.api.connection.dto.SupabaseIntrospectionData;
import com.broadcastmail.api.supabase.SupabaseSql;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SchemaIntrospectionService {

    private final SupabaseIntrospectionService supabaseIntrospectionService;

    public SchemaIntrospectionResult process(SupabaseIntrospectionData data) {
        if (data.candidateTables().isEmpty()) return new SchemaIntrospectionResult.NotDetected();

        List<DetectedColumn> authColumns = buildAuthColumns(data);

        List<SchemaIntrospectionResult.Detected> detected = data.candidateTables().stream()
                .map(candidate -> buildDetected(data, candidate, authColumns))
                .toList();

        return detected.size() == 1
                ? detected.getFirst()
                : new SchemaIntrospectionResult.MultipleCandidates(detected);
    }

    public SchemaIntrospectionResult introspect(String jdbcUrl, String encryptedRolePassword) {
        return process(supabaseIntrospectionService.introspect(jdbcUrl, encryptedRolePassword));
    }

    public SchemaIntrospectionResult.Detected introspectTable(
            String jdbcUrl, String encryptedRolePassword, String schema, String table, String idColumn) {
        SupabaseIntrospectionData data =
                supabaseIntrospectionService.introspectSingleTable(jdbcUrl, encryptedRolePassword, schema, table, idColumn);
        return switch (process(data)) {
            case SchemaIntrospectionResult.Detected d -> d;
            default -> throw new IllegalStateException("Expected detected table: " + schema + "." + table);
        };
    }

    private List<DetectedColumn> buildAuthColumns(SupabaseIntrospectionData data) {
        List<SupabaseIntrospectionData.Column> authUserColumns = data.columnsByTable().get("auth.users");
        if (authUserColumns == null) return List.of();

        Map<String, Integer> authCardinalities = data.cardinalities().getOrDefault("auth.user_emails", Map.of());

        List<DetectedColumn> authColumns = new ArrayList<>();
        for (SupabaseIntrospectionData.Column col : authUserColumns) {
            if (!SupabaseSql.AUTH_METADATA_COLUMNS.contains(col.name())) continue;
            String uiType = mapToUiType(col.dataType());
            int cardinality = authCardinalities.getOrDefault(col.name(), 0);
            authColumns.add(new DetectedColumn(col.name(), uiType, true, cardinality, cardinality > 50));
        }
        return authColumns;
    }

    private SchemaIntrospectionResult.Detected buildDetected(
            SupabaseIntrospectionData data,
            SupabaseIntrospectionData.CandidateTable ref,
            List<DetectedColumn> authColumns) {
        String tableKey = ref.schema() + "." + ref.table();
        List<SupabaseIntrospectionData.Column> profileColumns = data.columnsByTable().get(tableKey);
        Map<String, Integer> tableCardinalities = data.cardinalities().getOrDefault(tableKey, Map.of());

        List<DetectedColumn> filterableColumns = new ArrayList<>();
        if (profileColumns != null) {
            for (SupabaseIntrospectionData.Column col : profileColumns) {
                if (isNonFilterable(col.name())) continue;
                String uiType = mapToUiType(col.dataType());
                int cardinality = tableCardinalities.getOrDefault(col.name(), 0);
                filterableColumns.add(new DetectedColumn(col.name(), uiType, true, cardinality, cardinality > 50));
            }
        }

        // Synthetic fallback — skipped when the table already has a real created_at, which the loop above added.
        boolean hasCreatedAt = profileColumns != null
                && profileColumns.stream().anyMatch(c -> c.name().equals("created_at"));
        if (!hasCreatedAt) {
            filterableColumns.add(new DetectedColumn("created_at", "timestamptz", true, 0, false));
        }

        return new SchemaIntrospectionResult.Detected(
                ref.table(), ref.schema(), ref.idColumn(), filterableColumns, authColumns);
    }

    private boolean isNonFilterable(String columnName) {
        return columnName.equals("id") ||
                columnName.equals("email") ||
                columnName.endsWith("_url") ||
                columnName.endsWith("_token") ||
                columnName.endsWith("_hash");
    }

    private String mapToUiType(String postgresType) {
        return switch (postgresType.toLowerCase()) {
            case "boolean" -> "boolean";
            case "integer", "bigint", "numeric", "real", "double precision" -> "integer";
            case "timestamp with time zone", "timestamp without time zone", "date" -> "timestamptz";
            default -> "text";
        };
    }
}
