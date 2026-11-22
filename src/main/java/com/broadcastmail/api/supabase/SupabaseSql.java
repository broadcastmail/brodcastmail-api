package com.broadcastmail.api.supabase;

import org.hibernate.query.SortDirection;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class SupabaseSql {

    private SupabaseSql() {}

    public static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-zA-Z_]\\w*");

    public static final String AUTH_ALIAS = "auth";
    public static final String EMAIL_ALIAS = "email";
    public static final String READER_ROLE = "broadcastmail_reader";
    public static final String ORDER_BY = "ORDER BY ";

    static final String SCHEMA_LABEL = "schema";
    static final String TABLE_LABEL = "table";
    static final String COUNT_FROM = "SELECT COUNT(*) FROM \"";
    static final String QDOT = "\".\"";
    static final String LINKED_JOIN = "\" linked ON linked.\"";

    public record SortSpec(String alias, String key, SortDirection direction) {}

    public static final String READER_ROLE_PREFIX = "broadcastmail_r_";

    private static final SecureRandom ROLE_RANDOM = new SecureRandom();

    /** Schemas never offered for selection — Supabase internals plus our own. */
    public static final String SYSTEM_SCHEMAS_SQL = """
            'pg_catalog', 'information_schema', 'auth', 'storage', 'realtime', 'extensions',
            'graphql', 'graphql_public', 'vault', 'pgsodium', 'pgsodium_masks', 'net', 'cron',
            'supabase_functions', 'supabase_migrations', 'pgbouncer', 'pgtle', 'topology', '_realtime',
            '_analytics', '_supavisor', '_broadcastmail'""";

    private static final String USER_SCHEMA_FILTER =
            "%1$s NOT IN (" + SYSTEM_SCHEMAS_SQL + ") AND %1$s NOT LIKE 'pg\\_%%'";

    public record TableRef(String schema, String table) {
        public TableRef {
            requireIdentifier(schema, SCHEMA_LABEL);
            requireIdentifier(table, TABLE_LABEL);
        }

        public String qualified() {
            return quoteIdent(schema) + "." + quoteIdent(table);
        }
    }

    public static String newReaderRole() {
        byte[] bytes = new byte[5];
        ROLE_RANDOM.nextBytes(bytes);
        return READER_ROLE_PREFIX + HexFormat.of().formatHex(bytes);
    }

    /** Roles created per integration (safe to drop); the legacy shared role is never dropped. */
    public static boolean isPerIntegrationRole(String role) {
        return role != null && role.startsWith(READER_ROLE_PREFIX);
    }

    public static String quoteIdent(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    /**
     * Creates the reader role (never ALTERs an existing one — the pooler caches secrets, so passwords are set once)
     * plus the auth.users view, and grants SELECT only on the given tables.
     */
    public static String provisionReaderSql(String role, String password, Collection<TableRef> tables) {
        requireIdentifier(role, "role");
        requirePassword(password);
        return """
                CREATE ROLE %1$s NOINHERIT LOGIN PASSWORD '%2$s';
                CREATE SCHEMA IF NOT EXISTS _broadcastmail;
                CREATE OR REPLACE VIEW _broadcastmail.user_emails AS SELECT
                    id, email, created_at, updated_at, confirmed_at, email_confirmed_at,
                    phone, phone_confirmed_at, last_sign_in_at, banned_until, is_anonymous,
                    raw_app_meta_data, raw_user_meta_data
                FROM auth.users;
                GRANT USAGE ON SCHEMA _broadcastmail TO %1$s;
                GRANT SELECT ON _broadcastmail.user_emails TO %1$s;
                """.formatted(role, password) + grantReaderSql(role, tables);
    }

    public static String grantReaderSql(String role, Collection<TableRef> tables) {
        requireIdentifier(role, "role");
        StringBuilder sql = new StringBuilder();
        for (TableRef t : tables) {
            sql.append("GRANT USAGE ON SCHEMA ").append(quoteIdent(t.schema())).append(" TO ").append(role).append(";\n");
            sql.append("GRANT SELECT ON ").append(t.qualified()).append(" TO ").append(role).append(";\n");
        }
        return sql.toString();
    }

    /**
     * REVOKEs SELECT on every table the role can read except {@code keep} (and our own _broadcastmail view). The
     * database is the source of truth for what was granted, so callers never track previous grants.
     */
    public static String revokeOtherReaderGrantsSql(String role, Collection<TableRef> keep) {
        requireIdentifier(role, "role");
        // Identifiers are validated by TableRef, so they're safe inside single-quoted literals.
        String keepClause = keep.isEmpty() ? "" : "\n                      AND (table_schema, table_name) NOT IN ("
                + keep.stream().map(t -> "('" + t.schema() + "', '" + t.table() + "')").collect(Collectors.joining(", "))
                + ")";
        return """
                DO $$
                DECLARE t record;
                BEGIN
                    FOR t IN SELECT DISTINCT table_schema, table_name FROM information_schema.role_table_grants
                    WHERE grantee = '%1$s' AND privilege_type = 'SELECT'
                      AND table_schema <> '_broadcastmail'%2$s
                    LOOP
                        EXECUTE format('REVOKE SELECT ON %%I.%%I FROM %1$s', t.table_schema, t.table_name);
                    END LOOP;
                END$$;
                """.formatted(role, keepClause);
    }

    public static String dropReaderRoleSql(String role) {
        requireIdentifier(role, "role");
        if (!isPerIntegrationRole(role)) throw new IllegalArgumentException("Refusing to drop shared role " + role);
        return """
                DO $$
                BEGIN
                    IF EXISTS (SELECT FROM pg_roles WHERE rolname = '%1$s') THEN
                        DROP OWNED BY %1$s;
                        DROP ROLE %1$s;
                    END IF;
                END$$;
                """.formatted(role);
    }

    private static void requirePassword(String password) {
        // generatePassword() is URL-safe base64; anything else could break out of the SQL literal.
        if (password == null || !password.matches("[A-Za-z0-9_-]{16,}")) {
            throw new IllegalArgumentException("Invalid role password format");
        }
    }

    public static final Set<String> AUTH_METADATA_COLUMNS = Set.of(
            "created_at", "updated_at", "confirmed_at", "email_confirmed_at",
            "phone", "phone_confirmed_at", "last_sign_in_at", "banned_until", "is_anonymous"
    );

    public static final String INTROSPECT_SCHEMA = """
                SELECT table_schema, table_name, column_name, data_type
                FROM information_schema.columns
                WHERE (table_schema = 'auth' AND table_name = 'users')
                   OR (%s)
                ORDER BY table_schema, table_name, ordinal_position
                """.formatted(USER_SCHEMA_FILTER.formatted("table_schema"));
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
              AND %s
            """.formatted(USER_SCHEMA_FILTER.formatted("kcu.table_schema"));
    public static final String FIND_ALL_PRIMARY_KEYS = """
            SELECT tc.table_schema || '.' || tc.table_name AS table_key, kcu.column_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
                ON tc.constraint_name = kcu.constraint_name
                AND tc.table_schema = kcu.table_schema
                AND tc.table_name = kcu.table_name
            WHERE tc.constraint_type = 'PRIMARY KEY'
                AND %s
            ORDER BY tc.table_schema, tc.table_name, kcu.ordinal_position
            """.formatted(USER_SCHEMA_FILTER.formatted("tc.table_schema"));

    public static final String FIND_TABLE_PRIMARY_KEY = """
            SELECT kcu.column_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
                ON tc.constraint_name = kcu.constraint_name
                AND tc.table_schema = kcu.table_schema
                AND tc.table_name = kcu.table_name
            WHERE tc.constraint_type = 'PRIMARY KEY'
                AND tc.table_schema = '%s'
                AND tc.table_name = '%s'
            ORDER BY kcu.ordinal_position
            LIMIT 1
            """;

    public static final String RESOLVE_RECIPIENTS = "SELECT id, email FROM _broadcastmail.user_emails";
    public static final String COUNT_RECIPIENTS = "SELECT COUNT(*) FROM _broadcastmail.user_emails";

    public static final int EMAIL_SAMPLE_SIZE = 10;

    public static void requireIdentifier(String value, String label) {
        if (value == null || !IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
    }

    // auth.users always exists, unlike _broadcastmail.user_emails (a view GRANT_AUTH_ACCESS creates) —
    // usable to preview a project's user count before it's been selected/set up.
    public static final String COUNT_AUTH_USERS = "SELECT COUNT(*) FROM auth.users";

    public static String buildProjectUrl(String projectRef) {
        return "https://" + projectRef + ".supabase.co";
    }
}
