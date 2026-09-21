package com.broadcastmail.api.supabase;

public final class SupabaseSql {

    private SupabaseSql() {} // prevent instantiation

    public static final String CREATE_READER_ROLE = """
        CREATE ROLE broadcastmail_reader NOINHERIT LOGIN PASSWORD '%s';
        GRANT USAGE ON SCHEMA public TO broadcastmail_reader;
        GRANT USAGE ON SCHEMA auth TO broadcastmail_reader;
        GRANT SELECT ON ALL TABLES IN SCHEMA public TO broadcastmail_reader;
        CREATE VIEW auth.user_emails AS SELECT id, email FROM auth.users;
        GRANT SELECT ON auth.user_emails TO broadcastmail_reader;
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
                kcu.table_name,
                kcu.column_name
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
            """;
    public static final String RESOLVE_RECIPIENTS = "SELECT id, email FROM auth.user_emails";
    public static final String COUNT_RECIPIENTS = "SELECT COUNT(*) FROM auth.user_emails";

    /**
     * Recipient count joined to the linked profile table, for when a campaign has
     * filters on profile-table columns — those columns don't exist on auth.user_emails,
     * so COUNT_RECIPIENTS alone can't apply them. An INNER JOIN, so it must only be used
     * when there's at least one such filter: with none, it would wrongly exclude any
     * auth user who doesn't yet have a profile row, undercounting COUNT_RECIPIENTS' plain result.
     * Filter fragments stay unqualified (see CampaignFilterSerializer) because
     * auth.user_emails only exposes id/email, and filterable columns never include
     * either — so there's no ambiguity for Postgres to resolve between the two tables.
     */
    public static String buildRecipientCountQuery(String userTableSchema, String userTableName, String userIdColumn) {
        requireIdentifier(userTableSchema, "schema");
        requireIdentifier(userTableName, "table");
        requireIdentifier(userIdColumn, "id column");
        return "SELECT COUNT(*) FROM auth.user_emails ue JOIN \"" + userTableSchema + "\".\"" + userTableName
                + "\" p ON p.\"" + userIdColumn + "\" = ue.id";
    }

    private static void requireIdentifier(String value, String label) {
        if (value == null || !value.matches("[a-zA-Z_]\\w*")) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
    }

    // auth.users always exists, unlike auth.user_emails (a view CREATE_READER_ROLE creates) —
    // usable to preview a project's user count before it's been selected/set up.
    public static final String COUNT_AUTH_USERS = "SELECT COUNT(*) FROM auth.users";
    public static String buildJdbcUrl(String projectRef) {
        return "jdbc:postgresql://db." + projectRef + ".supabase.co:5432/postgres";
    }
    public static String buildProjectUrl(String projectRef) {
        return "https://" + projectRef + ".supabase.co";
    }
}
