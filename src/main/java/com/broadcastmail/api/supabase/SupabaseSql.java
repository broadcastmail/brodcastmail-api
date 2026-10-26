package com.broadcastmail.api.supabase;

import com.broadcastmail.api.audience.dto.AudienceListResponse;
import com.broadcastmail.api.campaign.filter.dto.FilterRequest;
import com.broadcastmail.common.campaign.filter.CampaignFilterSerializer;
import com.broadcastmail.common.campaign.filter.FilterOperator;
import com.broadcastmail.common.campaign.filter.FilterSource;
import com.broadcastmail.common.connection.Connection;
import org.hibernate.query.SortDirection;

import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class SupabaseSql {

    private SupabaseSql() {}

    public static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-zA-Z_]\\w*");

    public static final String AUTH_ALIAS = "auth";
    public static final String EMAIL_ALIAS = "email";
    public static final String READER_ROLE = "broadcastmail_reader";
    public static final String ORDER_BY = "ORDER BY ";

    private static final String SCHEMA_LABEL = "schema";
    private static final String TABLE_LABEL = "table";
    private static final String COUNT_FROM = "SELECT COUNT(*) FROM \"";
    private static final String QDOT = "\".\"";
    private static final String LINKED_JOIN = "\" linked ON linked.\"";

    public record SortSpec(String alias, String key, SortDirection direction) {}

    public static final String CREATE_READER_ROLE = """
        CREATE ROLE broadcastmail_reader NOINHERIT LOGIN PASSWORD '%s';
        GRANT USAGE ON SCHEMA public TO broadcastmail_reader;
        GRANT USAGE ON SCHEMA auth TO broadcastmail_reader;
        GRANT SELECT ON ALL TABLES IN SCHEMA public TO broadcastmail_reader;
        CREATE VIEW auth.user_emails AS SELECT
            id, email, created_at, updated_at, confirmed_at, email_confirmed_at,
            phone, phone_confirmed_at, last_sign_in_at, banned_until, is_anonymous,
            raw_app_meta_data, raw_user_meta_data
        FROM auth.users;
        GRANT SELECT ON auth.user_emails TO broadcastmail_reader;
        """;

    public static final Set<String> AUTH_METADATA_COLUMNS = Set.of(
            "created_at", "updated_at", "confirmed_at", "email_confirmed_at",
            "phone", "phone_confirmed_at", "last_sign_in_at", "banned_until", "is_anonymous"
    );

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
    public static final String FIND_ALL_PRIMARY_KEYS = """
            SELECT tc.table_schema || '.' || tc.table_name AS table_key, kcu.column_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
                ON tc.constraint_name = kcu.constraint_name
                AND tc.table_schema = kcu.table_schema
                AND tc.table_name = kcu.table_name
            WHERE tc.constraint_type = 'PRIMARY KEY'
                AND tc.table_schema NOT IN ('auth', 'information_schema', 'pg_catalog', 'pg_toast')
            ORDER BY tc.table_schema, tc.table_name, kcu.ordinal_position
            """;

    public static final String FIND_TABLE_PRIMARY_KEY = """
            SELECT kcu.column_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
                ON tc.constraint_name = kcu.constraint_name
                AND tc.table_schema = kcu.table_schema
                AND tc.table_name = kcu.table_name
            WHERE tc.constraint_type = 'PRIMARY KEY'
                AND tc.table_schema = ?
                AND tc.table_name = ?
            ORDER BY kcu.ordinal_position
            LIMIT 1
            """;

    public static final String RESOLVE_RECIPIENTS = "SELECT id, email FROM auth.user_emails";
    public static final String COUNT_RECIPIENTS = "SELECT COUNT(*) FROM auth.user_emails";

    public static final int EMAIL_SAMPLE_SIZE = 10;
    public static final String CHECK_EMAIL_COLUMN =
            "SELECT COUNT(*) > 0 AND COUNT(*) FILTER (WHERE \"%s\" ~ '^[^@ ]+@[^@ ]+\\.[^@ ]+$') = COUNT(*) " +
            "FROM (SELECT \"%s\" FROM \"%s\".\"%s\" WHERE \"%s\" IS NOT NULL LIMIT " + EMAIL_SAMPLE_SIZE + ") t";
    public static final String COUNT_DISTINCT_COLUMN =
            "SELECT COUNT(DISTINCT \"%s\") FROM \"%s\".\"%s\"";

    public static boolean usesAuthEmail(Connection connection) {
        return connection.getEmailColumn() == null;
    }

    public static String buildIdRef(Connection connection) {
        return usesAuthEmail(connection)
                ? AUTH_ALIAS + ".\"id\""
                : EMAIL_ALIAS + ".\"" + connection.getEmailTableIdColumn() + "\"";
    }

    public static String buildSortAlias(Connection connection, FilterSource sortSource) {
        if (sortSource == FilterSource.PROFILE_TABLE) return "linked";
        return usesAuthEmail(connection) ? AUTH_ALIAS : EMAIL_ALIAS;
    }

    public static String buildBaseCountQuery(Connection connection) {
        if (usesAuthEmail(connection)) {
            return COUNT_RECIPIENTS;
        }
        requireIdentifier(connection.getUserTableSchema(), SCHEMA_LABEL);
        requireIdentifier(connection.getUserTableName(), TABLE_LABEL);
        return COUNT_FROM + connection.getUserTableSchema() + QDOT + connection.getUserTableName() + "\"";
    }

    // Auth path: INNER JOIN — only call when filters exist; with none it undercounts (excludes users without a profile row).
    public static String buildRecipientCountQuery(Connection connection) {
        if (usesAuthEmail(connection)) {
            requireIdentifier(connection.getUserTableSchema(), SCHEMA_LABEL);
            requireIdentifier(connection.getUserTableName(), TABLE_LABEL);
            requireIdentifier(connection.getUserIdColumn(), "id column");
            return "SELECT COUNT(*) FROM auth.user_emails auth JOIN \""
                    + connection.getUserTableSchema() + QDOT + connection.getUserTableName()
                    + LINKED_JOIN + connection.getUserIdColumn() + "\" = auth.id";
        }
        String emailSchema = connection.getEmailTableSchema();
        String emailTable = connection.getEmailTableName();
        requireIdentifier(emailSchema, "email table schema");
        requireIdentifier(emailTable, "email table name");
        String join = connection.getUserTableName() != null
                ? " LEFT JOIN \"" + connection.getUserTableSchema() + QDOT + connection.getUserTableName()
                  + LINKED_JOIN + connection.getUserIdColumn() + "\" = " + EMAIL_ALIAS + ".\"" + connection.getEmailTableIdColumn() + "\""
                : "";
        return COUNT_FROM + emailSchema + QDOT + emailTable + "\" email" + join;
    }

    public static String buildEmailValidationQuery(String schema, String table, String emailColumn) {
        requireIdentifier(schema, SCHEMA_LABEL);
        requireIdentifier(table, TABLE_LABEL);
        requireIdentifier(emailColumn, "email column");
        return COUNT_FROM + schema + QDOT + table
                + "\" WHERE \"" + emailColumn + "\" IS NOT NULL AND \"" + emailColumn
                + "\" !~ '^[^@\\\\s]+@[^@\\\\s]+\\\\.[^@\\\\s]+$'";
    }

    private static void requireIdentifier(String value, String label) {
        if (value == null || !IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
    }

    // auth.users always exists, unlike auth.user_emails (a view CREATE_READER_ROLE creates) —
    // usable to preview a project's user count before it's been selected/set up.
    public static final String COUNT_AUTH_USERS = "SELECT COUNT(*) FROM auth.users";
    public static String buildJdbcUrl(String projectRef) {
        return "jdbc:postgresql://db." + projectRef + ".supabase.co:6543/postgres?prepareThreshold=0";
    }
    public static String buildProjectUrl(String projectRef) {
        return "https://" + projectRef + ".supabase.co";
    }
    public record AudienceQueryResult(long total, List<AudienceListResponse.AudienceRecipient> recipients) {}

    // Params order: countParams (baseWhere) first, then pageParams (whereClause), then LIMIT last.
    public static String buildAudienceQuery(Connection connection, boolean hasLinkedTable,
                                            String baseWhere, String whereClause, SortSpec sort) {
        String dir = sort.direction() == SortDirection.ASCENDING ? "ASC" : "DESC";

        if (!usesAuthEmail(connection)) {
            String emailSchema = connection.getEmailTableSchema();
            String emailTable = connection.getEmailTableName();
            String emailIdColumn = connection.getEmailTableIdColumn();
            String profileSchema = connection.getUserTableSchema();
            String profileTable = connection.getUserTableName();
            String profileIdColumn = connection.getUserIdColumn();
            requireIdentifier(emailSchema, "email table schema");
            requireIdentifier(emailTable, "email table name");
            requireIdentifier(emailIdColumn, "email table id column");
            requireIdentifier(connection.getEmailColumn(), "email column");
            String profileJoin = hasLinkedTable
                    ? "LEFT JOIN \"" + profileSchema + QDOT + profileTable + LINKED_JOIN + profileIdColumn + "\" = " + EMAIL_ALIAS + ".\"" + emailIdColumn + "\""
                    : "";
            String select = hasLinkedTable
                    ? EMAIL_ALIAS + ".\"" + emailIdColumn + "\" AS __id, " + EMAIL_ALIAS + ".\"" + connection.getEmailColumn() + "\" AS __email, linked.*"
                    : EMAIL_ALIAS + ".\"" + emailIdColumn + "\" AS __id, " + EMAIL_ALIAS + ".\"" + connection.getEmailColumn() + "\" AS __email";
            String orderBy = emailIdColumn.equals(sort.key())
                    ? ORDER_BY + EMAIL_ALIAS + ".\"" + emailIdColumn + "\" " + dir
                    : ORDER_BY + sort.alias() + ".\"" + sort.key() + "\" " + dir + " NULLS LAST, " + EMAIL_ALIAS + ".\"" + emailIdColumn + "\" " + dir;
            return """
                    WITH base_count AS (
                        SELECT COUNT(*) AS __total
                        FROM "%s"."%s" email %s %s
                    ),
                    page AS (
                        SELECT %s
                        FROM "%s"."%s" email %s %s %s LIMIT ?
                    )
                    SELECT page.*, (SELECT __total FROM base_count) AS __total FROM page
                    """.formatted(emailSchema, emailTable, profileJoin, baseWhere,
                    select,
                    emailSchema, emailTable, profileJoin, whereClause, orderBy);
        }

        String schema = connection.getUserTableSchema();
        String table = connection.getUserTableName();
        String idColumn = connection.getUserIdColumn();
        String join = buildLinkedTableJoin(hasLinkedTable, schema, table, idColumn);
        String select = hasLinkedTable ? AUTH_ALIAS + ".id AS __id, " + AUTH_ALIAS + ".email AS __email, linked.*" : AUTH_ALIAS + ".id AS __id, " + AUTH_ALIAS + ".email AS __email";
        String orderBy = "id".equals(sort.key())
                ? ORDER_BY + AUTH_ALIAS + ".\"id\" " + dir
                : ORDER_BY + sort.alias() + ".\"" + sort.key() + "\" " + dir + " NULLS LAST, " + AUTH_ALIAS + ".\"id\" " + dir;
        return """
                WITH base_count AS (
                    SELECT COUNT(*) AS __total
                    FROM auth.user_emails auth %s %s
                ),
                page AS (
                    SELECT %s
                    FROM auth.user_emails auth %s %s %s LIMIT ?
                )
                SELECT page.*, (SELECT __total FROM base_count) AS __total FROM page
                """.formatted(join, baseWhere, select, join, whereClause, orderBy);
    }

    public static AudienceQueryResult queryAudience(java.sql.Connection conn, String sql,
                                                    List<Object> countParams, List<Object> pageParams,
                                                    int limit) throws SQLException {
        List<Object> params = new ArrayList<>(countParams);
        params.addAll(pageParams);
        params.add(limit);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) stmt.setObject(i + 1, params.get(i));
            ResultSet rs = stmt.executeQuery();
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            List<AudienceListResponse.AudienceRecipient> recipients = new ArrayList<>();
            long total = 0;
            while (rs.next()) {
                if (recipients.isEmpty()) total = rs.getLong("__total");
                UUID id = UUID.fromString(rs.getString("__id"));
                String email = rs.getString("__email");
                Map<String, Object> attributes = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    String label = meta.getColumnLabel(i);
                    if (label.equals("__id") || label.equals("__email") || label.equals("__total")) continue;
                    attributes.put(label, rs.getObject(i));
                }
                recipients.add(new AudienceListResponse.AudienceRecipient(id, email, attributes, false));
            }
            return new AudienceQueryResult(total, recipients);
        }
    }

    public static String buildCursorFragment(SortSpec sort, String idRef) {
        String op = sort.direction() == SortDirection.ASCENDING ? ">" : "<";
        if (idRef.endsWith(".\"" + sort.key() + "\"")) {
            return idRef + " " + op + " ?";
        }
        return "(" + sort.alias() + ".\"" + sort.key() + "\" " + op + " ? OR ("
                + sort.alias() + ".\"" + sort.key() + "\" = ? AND " + idRef + " " + op + " ?))";
    }

    public static List<Object> buildCursorParams(String sortKey, String lastSortVal, UUID lastId) {
        if ("id".equals(sortKey)) {
            return List.of(lastId);
        }
        return Arrays.asList(lastSortVal, lastSortVal, lastId);
    }

    private static String buildLinkedTableJoin(boolean hasLinkedTable, String schema, String table, String idColumn) {
        return hasLinkedTable
                ? "LEFT JOIN \"" + schema + QDOT + table + LINKED_JOIN + idColumn + "\" = auth.id"
                : "";
    }

    public static String buildWhereClause(List<FilterRequest> filters) {
        if (filters.isEmpty()) return "";
        return "WHERE " + filters.stream().map(SupabaseSql::buildFilterFragment).collect(Collectors.joining(" AND "));
    }

    public static List<Object> buildFilterParams(List<FilterRequest> filters) {
        return filters.stream()
                .map(f -> f.operator() == FilterOperator.CONTAINS ? "%" + f.filterValue() + "%" : f.filterValue())
                .collect(Collectors.toList());
    }

    private static String buildFilterFragment(FilterRequest filter) {
        if (!IDENTIFIER_PATTERN.matcher(filter.columnName()).matches()) {
            throw new IllegalArgumentException("Invalid column name: " + filter.columnName());
        }
        String op = switch (filter.operator()) {
            case EQ -> "=";
            case NEQ -> "!=";
            case GT -> ">";
            case LT -> "<";
            case CONTAINS -> "ILIKE";
        };
        return switch (filter.sourceOrDefault()) {
            case PROFILE_TABLE, AUTH_METADATA -> "\"" + filter.columnName() + "\" " + op + " ?";
            case AUTH_METADATA_JSON -> {
                if (!CampaignFilterSerializer.JSON_METADATA_COLUMNS.contains(filter.columnName())) {
                    throw new IllegalArgumentException("Invalid metadata column: " + filter.columnName());
                }
                if (filter.jsonKey() == null || !IDENTIFIER_PATTERN.matcher(filter.jsonKey()).matches()) {
                    throw new IllegalArgumentException("Invalid metadata key: " + filter.jsonKey());
                }
                yield "\"" + filter.columnName() + "\"->>'" + filter.jsonKey() + "' " + op + " ?";
            }
        };
    }
}
