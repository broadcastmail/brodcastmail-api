package com.broadcastmail.api.connection.introspection;

import com.broadcastmail.api.connection.dto.SupabaseIntrospectionData;
import com.broadcastmail.api.supabase.management.ManagementSqlRunner;
import com.broadcastmail.api.supabase.SupabaseSql;
import com.broadcastmail.api.supabase.SupabaseSql.TableRef;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.broadcastmail.api.connection.introspection.SqlRows.isIdentifier;


@Component
@RequiredArgsConstructor
class ColumnStatsCollector {

    static final int UNKNOWN_CARDINALITY = 999;

    // Types without an equality operator — COUNT(DISTINCT ...) errors on them.
    private static final Set<String> NON_COMPARABLE_TYPES =
            Set.of("json", "xml", "point", "line", "lseg", "box", "path", "polygon", "circle");

    private final ColumnProbeRunner probeRunner;

    record ColumnStats(Map<String, Map<String, Integer>> cardinalities, Map<String, List<String>> emailColumnsByTable) {}

    ColumnStats collect(ManagementSqlRunner sql,
                        List<SupabaseIntrospectionData.CandidateTable> candidates,
                        Map<String, List<SupabaseIntrospectionData.Column>> columnsByTable) {
        List<ColumnProbe> probes = new ArrayList<>();
        probes.addAll(cardinalityProbes(candidates, columnsByTable));
        probes.addAll(emailProbes(columnsByTable));
        Map<ColumnProbe, Long> results = probeRunner.run(sql, probes);

        Map<String, Map<String, Integer>> cardinalities = new HashMap<>();
        Map<String, List<String>> emailColumnsByTable = new LinkedHashMap<>();
        for (ColumnProbe probe : probes) {
            Long value = results.get(probe);
            if (probe.kind() == ColumnProbe.Kind.CARDINALITY) {
                int cardinality = value == null ? UNKNOWN_CARDINALITY : (int) Math.min(value, Integer.MAX_VALUE);
                cardinalities.computeIfAbsent(probe.resultKey(), k -> new HashMap<>()).put(probe.column(), cardinality);
            } else if (value != null && value == 1L) {
                emailColumnsByTable.computeIfAbsent(probe.resultKey(), k -> new ArrayList<>()).add(probe.column());
            }
        }
        return new ColumnStats(cardinalities, emailColumnsByTable);
    }

    private List<ColumnProbe> cardinalityProbes(
            List<SupabaseIntrospectionData.CandidateTable> candidates,
            Map<String, List<SupabaseIntrospectionData.Column>> columnsByTable) {
        List<ColumnProbe> probes = new ArrayList<>();
        for (SupabaseIntrospectionData.CandidateTable candidate : candidates) {
            probes.addAll(candidateCardinalityProbes(candidate, columnsByTable));
        }
        probes.addAll(authMetadataCardinalityProbes(columnsByTable));
        return probes;
    }

    private List<ColumnProbe> candidateCardinalityProbes(
            SupabaseIntrospectionData.CandidateTable candidate,
            Map<String, List<SupabaseIntrospectionData.Column>> columnsByTable) {
        String key = candidate.schema() + "." + candidate.table();
        List<SupabaseIntrospectionData.Column> columns = columnsByTable.get(key);
        if (columns == null || !isIdentifier(candidate.schema()) || !isIdentifier(candidate.table())) {
            return List.of();
        }
        TableRef source = new TableRef(candidate.schema(), candidate.table());
        return columns.stream()
                .filter(col -> isIdentifier(col.name()) && !NON_COMPARABLE_TYPES.contains(col.dataType()))
                .map(col -> new ColumnProbe(ColumnProbe.Kind.CARDINALITY, key, source, col.name()))
                .toList();
    }

    // auth.users is read directly: the _broadcastmail.user_emails view may not exist before provisioning.
    private List<ColumnProbe> authMetadataCardinalityProbes(
            Map<String, List<SupabaseIntrospectionData.Column>> columnsByTable) {
        List<SupabaseIntrospectionData.Column> authCols = columnsByTable.get("auth.users");
        if (authCols == null) {
            return List.of();
        }
        TableRef authUsers = new TableRef("auth", "users");
        return authCols.stream()
                .filter(col -> SupabaseSql.AUTH_METADATA_COLUMNS.contains(col.name()))
                .map(col -> new ColumnProbe(ColumnProbe.Kind.CARDINALITY,
                        SupabaseIntrospectionService.AUTH_CARDINALITY_KEY, authUsers, col.name()))
                .toList();
    }

    private List<ColumnProbe> emailProbes(Map<String, List<SupabaseIntrospectionData.Column>> columnsByTable) {
        List<ColumnProbe> probes = new ArrayList<>();
        columnsByTable.forEach((key, columns) -> {
            if (key.startsWith("auth.")) return;
            int dot = key.indexOf('.');
            String schema = key.substring(0, dot);
            String table = key.substring(dot + 1);
            if (!isIdentifier(schema) || !isIdentifier(table)) return;
            TableRef source = new TableRef(schema, table);
            for (SupabaseIntrospectionData.Column col : columns) {
                boolean textual = col.dataType().equals("text") || col.dataType().startsWith("character varying");
                if (textual && isIdentifier(col.name())) {
                    probes.add(new ColumnProbe(ColumnProbe.Kind.EMAIL, key, source, col.name()));
                }
            }
        });
        return probes;
    }
}
