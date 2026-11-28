package com.broadcastmail.api.connection.introspection;

import com.broadcastmail.api.connection.dto.SupabaseIntrospectionData;
import com.broadcastmail.api.supabase.management.ManagementSqlRunner;
import com.broadcastmail.api.supabase.SupabaseSql.TableRef;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Reads a tenant project's schema through a {@link ManagementSqlRunner} (Management API, running as postgres), so it sees
 * every user schema and does not depend on the reader role. The only public entry point of this package — catalog
 * reads and per-column statistics live in package-private collaborators.
 */
@Service
@RequiredArgsConstructor
public class SupabaseIntrospectionService {

    /** Key under which auth.users column cardinalities are reported in {@link SupabaseIntrospectionData}. */
    public static final String AUTH_CARDINALITY_KEY = "_batchbeam.user_emails";

    private final SchemaCatalogReader catalogReader;
    private final ColumnStatsCollector columnStatsCollector;

    /** Detects candidate profile tables (FK to auth.users) and reads everything needed to choose between them. */
    public SupabaseIntrospectionData introspect(ManagementSqlRunner sql) {
        return build(sql, catalogReader.candidateTables(sql));
    }

    /** Same data as {@link #introspect}, for a table the user already picked. */
    public SupabaseIntrospectionData introspectSingleTable(ManagementSqlRunner sql, String schema, String table, String idColumn) {
        return build(sql, List.of(new SupabaseIntrospectionData.CandidateTable(schema, table, idColumn)));
    }

    public SupabaseIntrospectionData introspectAuthOnly(ManagementSqlRunner sql) {
        return build(sql, List.of());
    }

    public String detectPrimaryKey(ManagementSqlRunner sql, TableRef table) {
        return catalogReader.primaryKey(sql, table);
    }

    private SupabaseIntrospectionData build(ManagementSqlRunner sql, List<SupabaseIntrospectionData.CandidateTable> candidates) {
        Map<String, List<SupabaseIntrospectionData.Column>> columnsByTable = catalogReader.columnsByTable(sql);
        Map<String, String> primaryKeys = catalogReader.primaryKeys(sql);
        ColumnStatsCollector.ColumnStats stats = columnStatsCollector.collect(sql, candidates, columnsByTable);
        return new SupabaseIntrospectionData(
                candidates, columnsByTable, primaryKeys, stats.cardinalities(), stats.emailColumnsByTable());
    }
}
