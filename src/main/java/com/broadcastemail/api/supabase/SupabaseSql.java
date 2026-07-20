package com.broadcastemail.api.supabase;

public final class SupabaseSql {

    private SupabaseSql() {} // prevent instantiation

    public static final String CREATE_READER_ROLE = """
            CREATE ROLE broadcastmail_reader NOINHERIT LOGIN PASSWORD '%s';
            GRANT USAGE ON SCHEMA auth TO broadcastmail_reader;
            GRANT SELECT (id, email, created_at) ON auth.users TO broadcastmail_reader;
            GRANT USAGE ON SCHEMA public TO broadcastmail_reader;
            GRANT SELECT ON public.profiles TO broadcastmail_reader;
            """;

    public static final String INTROSPECT_SCHEMA = """
                SELECT table_schema, table_name, column_name, data_type
                FROM information_schema.columns
                WHERE table_schema IN ('public', 'auth')
                ORDER BY table_schema, table_name, ordinal_position
                """;
    public static final String FIND_USER_LINKED_TABLES = """
            SELECT
                kcu.table_schema,
                kcu.table_name
            FROM information_schema.referential_constraints rc
            JOIN information_schema.key_column_usage kcu
                ON kcu.constraint_name = rc.constraint_name
                AND kcu.table_schema = rc.constraint_schema
            JOIN information_schema.key_column_usage ref_kcu
                ON ref_kcu.constraint_name = rc.unique_constraint_name
                AND ref_kcu.table_schema = rc.unique_constraint_schema
            WHERE ref_kcu.table_schema = 'auth'
              AND ref_kcu.table_name = 'users'
              AND ref_kcu.column_name = 'id'
            LIMIT 1
            """;
    public static String buildJdbcUrl(String projectRef) {
        return "jdbc:postgresql://db." + projectRef + ".supabase.co:5432/postgres";
    }
    public static String buildProjectUrl(String projectRef) {
        return "https://" + projectRef + ".supabase.co";
    }
}
