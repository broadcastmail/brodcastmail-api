package com.broadcastmail.api.connection.introspection;

import com.broadcastmail.api.supabase.SupabaseSql;
import com.broadcastmail.api.supabase.SupabaseSql.TableRef;

/**
 * One scalar statistic about a column: read from {@code source}, reported under {@code resultKey}
 * (usually {@code schema.table}; auth.users is reported under the view's key).
 */
record ColumnProbe(Kind kind, String resultKey, TableRef source, String column) {

    enum Kind {
        CARDINALITY,
        EMAIL
    }

    String expression() {
        String col = SupabaseSql.quoteIdent(column);
        return switch (kind) {
            case CARDINALITY -> "(SELECT COUNT(DISTINCT " + col + ") FROM " + source.qualified() + ")";
            case EMAIL -> "(SELECT (COUNT(*) > 0 AND COUNT(*) FILTER (WHERE " + col
                    + " ~ '^[^@ ]+@[^@ ]+\\.[^@ ]+$') = COUNT(*))::int"
                    + " FROM (SELECT " + col + " FROM " + source.qualified() + " WHERE " + col + " IS NOT NULL LIMIT "
                    + SupabaseSql.EMAIL_SAMPLE_SIZE + ") t)";
        };
    }

    /** A UNION ALL branch returning {@code (probe, value)} so results can be matched back by index. */
    String selectSql(int index) {
        return "SELECT " + index + " AS probe, " + expression() + "::bigint AS value";
    }
}
