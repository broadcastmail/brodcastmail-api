package com.broadcastmail.api.connection.introspection;

import com.broadcastmail.api.supabase.management.ManagementSqlRunner;
import com.broadcastmail.api.supabase.SupabaseSql.TableRef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.broadcastmail.api.connection.introspection.SqlRows.number;

/**
 * Runs column probes with as few Management API round-trips as possible: all in one UNION ALL statement, falling
 * back to one statement per table if that fails (e.g. a type without equality). Probes on a table that still fails
 * are left out — callers treat missing results as unknown.
 */
@Slf4j
@Component
class ColumnProbeRunner {

    Map<ColumnProbe, Long> run(ManagementSqlRunner sql, List<ColumnProbe> probes) {
        if (probes.isEmpty()) return Map.of();
        try {
            return runBatch(sql, probes);
        } catch (RuntimeException e) {
            log.warn("[introspect] batched probes failed, retrying per table: {}", e.getMessage());
        }

        Map<TableRef, List<ColumnProbe>> byTable = new LinkedHashMap<>();
        probes.forEach(p -> byTable.computeIfAbsent(p.source(), k -> new ArrayList<>()).add(p));

        Map<ColumnProbe, Long> results = new HashMap<>();
        byTable.forEach((table, tableProbes) -> {
            try {
                results.putAll(runBatch(sql, tableProbes));
            } catch (RuntimeException e) {
                log.warn("[introspect] probes failed for {}.{}: {}", table.schema(), table.table(), e.getMessage());
            }
        });
        return results;
    }

    private Map<ColumnProbe, Long> runBatch(ManagementSqlRunner sql, List<ColumnProbe> probes) {
        StringJoiner union = new StringJoiner("\nUNION ALL\n");
        for (int i = 0; i < probes.size(); i++) {
            union.add(probes.get(i).selectSql(i));
        }
        Map<ColumnProbe, Long> results = new HashMap<>();
        for (Map<String, Object> row : sql.query(union.toString())) {
            Long index = number(row.get("probe"));
            Long value = number(row.get("value"));
            if (index != null && value != null) results.put(probes.get(index.intValue()), value);
        }
        return results;
    }
}
