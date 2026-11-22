package com.broadcastmail.api.connection.introspection;

import com.broadcastmail.api.connection.dto.SupabaseIntrospectionData;
import com.broadcastmail.api.supabase.management.ManagementSqlRunner;
import com.broadcastmail.api.supabase.SupabaseSql;
import com.broadcastmail.api.supabase.SupabaseSql.TableRef;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.broadcastmail.api.connection.introspection.SqlRows.string;

/** Reads structural metadata (tables, columns, primary keys) from information_schema. */
@Component
class SchemaCatalogReader {

    private static final String COLUMN_NAME = "column_name";

    /** Tables with a foreign key to auth.users.id, in any user schema. */
    List<SupabaseIntrospectionData.CandidateTable> candidateTables(ManagementSqlRunner sql) {
        return sql.query(SupabaseSql.FIND_USER_LINKED_TABLES).stream()
                .map(row -> new SupabaseIntrospectionData.CandidateTable(
                        string(row, "table_schema"), string(row, "table_name"), string(row, COLUMN_NAME)))
                .toList();
    }

    /** Columns per {@code schema.table}, in ordinal order — user schemas plus auth.users. */
    Map<String, List<SupabaseIntrospectionData.Column>> columnsByTable(ManagementSqlRunner sql) {
        Map<String, List<SupabaseIntrospectionData.Column>> columnsByTable = new LinkedHashMap<>();
        for (Map<String, Object> row : sql.query(SupabaseSql.INTROSPECT_SCHEMA)) {
            String key = string(row, "table_schema") + "." + string(row, "table_name");
            columnsByTable.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new SupabaseIntrospectionData.Column(string(row, COLUMN_NAME), string(row, "data_type")));
        }
        return columnsByTable;
    }

    /** First primary-key column per {@code schema.table}. */
    Map<String, String> primaryKeys(ManagementSqlRunner sql) {
        Map<String, String> result = new HashMap<>();
        for (Map<String, Object> row : sql.query(SupabaseSql.FIND_ALL_PRIMARY_KEYS)) {
            result.putIfAbsent(string(row, "table_key"), string(row, COLUMN_NAME));
        }
        return result;
    }

    String primaryKey(ManagementSqlRunner sql, TableRef table) {
        List<Map<String, Object>> rows = sql.query(
                SupabaseSql.FIND_TABLE_PRIMARY_KEY.formatted(table.schema(), table.table()));
        if (rows.isEmpty()) {
            throw new IllegalStateException("No primary key found for " + table.schema() + "." + table.table());
        }
        return string(rows.getFirst(), COLUMN_NAME);
    }
}
