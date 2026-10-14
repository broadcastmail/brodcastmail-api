package com.broadcastmail.api.supabase;

import com.broadcastmail.api.audience.dto.AudienceListResponse;
import com.broadcastmail.api.campaign.filter.dto.FilterRequest;
import com.broadcastmail.common.campaign.filter.CampaignFilterSerializer;
import com.broadcastmail.common.campaign.filter.FilterOperator;
import org.hibernate.query.SortDirection;

import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class SupabaseSql {

    private SupabaseSql() {}

    public static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-zA-Z_]\\w*");

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
        return "SELECT COUNT(*) FROM auth.user_emails auth JOIN \"" + userTableSchema + "\".\"" + userTableName
                + "\" linked ON linked.\"" + userIdColumn + "\" = auth.id";
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

    /**
     * Builds a CTE that fetches the page and the full filter-scoped count in one round-trip.
     * Params order: countParams (baseWhere) first, then pageParams (whereClause), then LIMIT last.
     */
    public static String buildAudienceQuery(boolean hasLinkedTable, String schema, String table, String idColumn,
                                            String baseWhere, String whereClause, SortSpec sort) {
        String join = buildLinkedTableJoin(hasLinkedTable, schema, table, idColumn);
        String select = hasLinkedTable ? "auth.id AS __id, auth.email AS __email, linked.*" : "auth.id AS __id, auth.email AS __email";
        String dir = sort.direction() == SortDirection.ASCENDING ? "ASC" : "DESC";
        String orderBy = "id".equals(sort.key())
                ? "ORDER BY auth.\"id\" " + dir
                : "ORDER BY " + sort.alias() + ".\"" + sort.key() + "\" " + dir + " NULLS LAST, auth.\"id\" " + dir;
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

    public static AudienceQueryResult queryAudience(Connection conn, String sql,
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
                recipients.add(new AudienceListResponse.AudienceRecipient(id, email, attributes));
            }
            return new AudienceQueryResult(total, recipients);
        }
    }

    public static String buildCursorFragment(SortSpec sort) {
        String op = sort.direction() == SortDirection.ASCENDING ? ">" : "<";
        if ("id".equals(sort.key())) {
            return "auth.\"id\" " + op + " ?";
        }
        return "(" + sort.alias() + ".\"" + sort.key() + "\" " + op + " ? OR ("
                + sort.alias() + ".\"" + sort.key() + "\" = ? AND auth.\"id\" " + op + " ?))";
    }

    public static List<Object> buildCursorParams(String sortKey, String lastSortVal, UUID lastId) {
        if ("id".equals(sortKey)) {
            return List.of(lastId);
        }
        return Arrays.asList(lastSortVal, lastSortVal, lastId);
    }

    private static String buildLinkedTableJoin(boolean hasLinkedTable, String schema, String table, String idColumn) {
        return hasLinkedTable
                ? "LEFT JOIN \"" + schema + "\".\"" + table + "\" linked ON linked.\"" + idColumn + "\" = auth.id"
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
