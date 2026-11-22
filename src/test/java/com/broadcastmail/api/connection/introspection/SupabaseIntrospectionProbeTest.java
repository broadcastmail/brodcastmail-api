package com.broadcastmail.api.connection.introspection;

import com.broadcastmail.api.connection.dto.SupabaseIntrospectionData;
import com.broadcastmail.api.supabase.management.ManagementSqlRunner;
import com.broadcastmail.api.supabase.SupabaseSql;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SupabaseIntrospectionProbeTest {

    private final SupabaseIntrospectionService service = new SupabaseIntrospectionService(
            new SchemaCatalogReader(), new ColumnStatsCollector(new ColumnProbeRunner()));

    /** Fake Management API: answers the metadata queries, and runs probe batches through {@code probes}. */
    private static final class FakeRunner implements ManagementSqlRunner {
        final List<String> probeQueries = new ArrayList<>();
        boolean failBatchOver = false;

        @Override
        public List<Map<String, Object>> query(String sql) {
            if (sql.equals(SupabaseSql.FIND_USER_LINKED_TABLES)) {
                return List.of(Map.of("table_schema", "crm", "table_name", "customers", "column_name", "id"));
            }
            if (sql.equals(SupabaseSql.INTROSPECT_SCHEMA)) {
                return List.of(
                        col("crm", "customers", "id", "uuid"),
                        col("crm", "customers", "plan", "text"),
                        col("crm", "customers", "contact", "character varying"),
                        col("crm", "customers", "meta", "json"));
            }
            if (sql.equals(SupabaseSql.FIND_ALL_PRIMARY_KEYS)) {
                return List.of(Map.of("table_key", "crm.customers", "column_name", "id"));
            }
            probeQueries.add(sql);
            if (failBatchOver && probeQueries.size() == 1) throw new IllegalStateException("batch failed");
            List<Map<String, Object>> rows = new ArrayList<>();
            int probes = sql.split("UNION ALL").length;
            for (int i = 0; i < probes; i++) {
                boolean isEmailProbe = sql.split("UNION ALL")[i].contains("COUNT(*) FILTER");
                boolean contactColumn = sql.split("UNION ALL")[i].contains("\"contact\"");
                rows.add(Map.of("probe", i, "value", isEmailProbe ? (contactColumn ? 1 : 0) : 7));
            }
            return rows;
        }

        private static Map<String, Object> col(String schema, String table, String name, String type) {
            return Map.of("table_schema", schema, "table_name", table, "column_name", name, "data_type", type);
        }
    }

    @Test
    void shouldBatchAllProbesIntoOneQueryAndSkipJsonForCardinality() {
        // Given
        FakeRunner runner = new FakeRunner();

        // When
        SupabaseIntrospectionData data = service.introspect(runner);

        // Then
        assertThat(runner.probeQueries).hasSize(1);
        assertThat(runner.probeQueries.getFirst()).doesNotContain("COUNT(DISTINCT \"meta\")");
        assertThat(data.cardinalities().get("crm.customers")).containsEntry("plan", 7).doesNotContainKey("meta");
        assertThat(data.emailColumnsByTable()).containsEntry("crm.customers", List.of("contact"));
        assertThat(data.candidateTables()).extracting(SupabaseIntrospectionData.CandidateTable::schema).containsExactly("crm");
    }

    @Test
    void shouldFallBackToPerTableQueriesWhenBatchFails() {
        // Given
        FakeRunner runner = new FakeRunner();
        runner.failBatchOver = true;

        // When
        SupabaseIntrospectionData data = service.introspect(runner);

        // Then
        assertThat(runner.probeQueries).hasSize(2);
        assertThat(data.cardinalities().get("crm.customers")).containsEntry("plan", 7);
    }
}
