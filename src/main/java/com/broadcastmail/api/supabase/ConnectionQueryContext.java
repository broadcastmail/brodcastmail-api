package com.broadcastmail.api.supabase;

import com.broadcastmail.common.campaign.filter.FilterSource;
import com.broadcastmail.common.connection.Connection;
import org.hibernate.query.SortDirection;

import static com.broadcastmail.api.supabase.SupabaseSql.*;

public final class ConnectionQueryContext {

    private final boolean authMode;
    private final String idRef;
    private final String emailColumnExpression;
    private final boolean hasLinkedUserTable;

    private final String userSchema;
    private final String userTable;
    private final String userIdColumn;

    private final String emailSchema;
    private final String emailTable;
    private final String emailIdColumn;
    private final String emailColumn;

    private ConnectionQueryContext(Connection connection) {
        this.authMode = connection.getEmailColumn() == null;
        this.userSchema = connection.getUserTableSchema();
        this.userTable = connection.getUserTableName();
        this.userIdColumn = connection.getUserIdColumn();
        this.hasLinkedUserTable = userTable != null;

        if (authMode) {
            this.emailSchema = null;
            this.emailTable = null;
            this.emailIdColumn = null;
            this.emailColumn = null;
            this.idRef = AUTH_ALIAS + ".\"id\"";
            this.emailColumnExpression = AUTH_ALIAS + ".email";
        } else {
            this.emailSchema = connection.getEmailTableSchema();
            this.emailTable = connection.getEmailTableName();
            this.emailIdColumn = connection.getEmailTableIdColumn();
            this.emailColumn = connection.getEmailColumn();
            this.idRef = EMAIL_ALIAS + ".\"" + emailIdColumn + "\"";
            this.emailColumnExpression = EMAIL_ALIAS + ".\"" + emailColumn + "\"";
        }
    }

    public static ConnectionQueryContext from(Connection connection) {
        return new ConnectionQueryContext(connection);
    }

    public String idRef() {
        return idRef;
    }

    /** Returns the qualified email column expression for use in WHERE clauses (e.g. suppression filter). */
    public String emailColumnExpression() {
        return emailColumnExpression;
    }

    public SupabaseSql.SortSpec buildSortSpec(FilterSource sortSource, String sortKey, SortDirection direction) {
        String alias = sortSource == FilterSource.PROFILE_TABLE ? "linked"
                : authMode ? AUTH_ALIAS : EMAIL_ALIAS;
        return new SupabaseSql.SortSpec(alias, sortKey, direction);
    }

    public String buildBaseCountQuery() {
        if (authMode) return COUNT_RECIPIENTS;
        requireIdentifier(emailSchema, SCHEMA_LABEL);
        requireIdentifier(emailTable, TABLE_LABEL);
        return COUNT_FROM + emailSchema + QDOT + emailTable + "\"";
    }

    public String buildRecipientCountQuery() {
        if (authMode) {
            requireIdentifier(userSchema, SCHEMA_LABEL);
            requireIdentifier(userTable, TABLE_LABEL);
            requireIdentifier(userIdColumn, "id column");
            return "SELECT COUNT(*) FROM auth.user_emails auth JOIN \""
                    + userSchema + QDOT + userTable
                    + LINKED_JOIN + userIdColumn + "\" = auth.id";
        }
        requireIdentifier(emailSchema, "email table schema");
        requireIdentifier(emailTable, "email table name");
        String join = hasLinkedUserTable
                ? " LEFT JOIN \"" + userSchema + QDOT + userTable
                  + LINKED_JOIN + userIdColumn + "\" = " + EMAIL_ALIAS + ".\"" + emailIdColumn + "\""
                : "";
        return COUNT_FROM + emailSchema + QDOT + emailTable + "\" email" + join;
    }

    public String buildAudienceQuery(String baseWhere, String whereClause, SupabaseSql.SortSpec sort) {
        String dir = sort.direction() == SortDirection.ASCENDING ? "ASC" : "DESC";

        if (!authMode) {
            requireIdentifier(emailSchema, "email table schema");
            requireIdentifier(emailTable, "email table name");
            requireIdentifier(emailIdColumn, "email table id column");
            requireIdentifier(emailColumn, "email column");
            String profileJoin = hasLinkedUserTable
                    ? "LEFT JOIN \"" + userSchema + QDOT + userTable + LINKED_JOIN + userIdColumn + "\" = " + EMAIL_ALIAS + ".\"" + emailIdColumn + "\""
                    : "";
            String select = hasLinkedUserTable
                    ? EMAIL_ALIAS + ".\"" + emailIdColumn + "\" AS __id, " + emailColumnExpression + " AS __email, linked.*"
                    : EMAIL_ALIAS + ".\"" + emailIdColumn + "\" AS __id, " + emailColumnExpression + " AS __email";
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

        String join = hasLinkedUserTable
                ? "LEFT JOIN \"" + userSchema + QDOT + userTable + LINKED_JOIN + userIdColumn + "\" = auth.id"
                : "";
        String select = hasLinkedUserTable
                ? AUTH_ALIAS + ".id AS __id, " + AUTH_ALIAS + ".email AS __email, linked.*"
                : AUTH_ALIAS + ".id AS __id, " + AUTH_ALIAS + ".email AS __email";
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
}
