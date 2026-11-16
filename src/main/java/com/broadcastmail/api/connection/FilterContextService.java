package com.broadcastmail.api.connection;

import com.broadcastmail.api.common.exceptions.ConnectionNotFoundException;
import com.broadcastmail.api.connection.dto.FilterContextResponse;
import com.broadcastmail.api.supabase.TenantConnectionGateway;
import com.broadcastmail.common.campaign.filter.FilterSource;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import com.broadcastmail.common.filterablecolumn.FilterableColumn;
import com.broadcastmail.common.filterablecolumn.FilterableColumnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class FilterContextService {

    static final int ENUM_CARDINALITY_THRESHOLD = 20;
    private static final int ENUM_VALUES_LIMIT = 50;

    private static final String TYPE_BOOLEAN = "boolean";
    private static final String TYPE_DATETIME = "datetime";
    private static final String TYPE_NUMBER   = "number";
    private static final String TYPE_ENUM     = "enum";
    private static final String TYPE_TEXT     = "text";

    private static final String OP_EQUALS     = "equals";
    private static final String OP_NOT_EQUALS = "not_equals";

    private final ConnectionRepository connectionRepository;
    private final FilterableColumnRepository filterableColumnRepository;
    private final TenantConnectionGateway connectionGateway;

    public FilterContextResponse build(UUID accountId) {
        Connection connection = connectionRepository.findByAccountId(accountId)
                .orElseThrow(ConnectionNotFoundException::new);

        List<FilterableColumn> all = filterableColumnRepository.findByConnectionId(connection.getId());

        List<FilterableColumn> profileCols = all.stream()
                .filter(c -> c.getSource() == FilterSource.PROFILE_TABLE)
                .toList();
        List<FilterableColumn> authCols = all.stream()
                .filter(c -> c.getSource() == FilterSource.AUTH_METADATA)
                .toList();

        Map<String, List<String>> enumValuesMap = fetchEnumValues(connection, profileCols);

        List<FilterContextResponse.FilterColumnInfo> profile = profileCols.stream()
                .map(c -> toColumnInfo(c, enumValuesMap.get(c.getColumnName())))
                .toList();
        List<FilterContextResponse.FilterColumnInfo> auth = authCols.stream()
                .map(c -> toColumnInfo(c, null))
                .toList();

        return new FilterContextResponse(
                "DETECTED",
                new FilterContextResponse.UserTableInfo(
                        connection.getUserTableSchema(),
                        connection.getUserTableName(),
                        connection.getUserIdColumn()),
                recipientSource(connection),
                new FilterContextResponse.ColumnsInfo(profile, auth)
        );
    }

    private FilterContextResponse.FilterColumnInfo toColumnInfo(FilterableColumn col, List<String> enumValues) {
        String dataType = resolveDataType(col.getColumnType(), col.getCardinality(), col.getCardinalityWarning());
        ColumnRecommendationService.Recommendation rec = ColumnRecommendationService.recommend(col.getColumnName(), dataType);
        return new FilterContextResponse.FilterColumnInfo(
                col.getColumnName(),
                prettify(col.getColumnName()),
                dataType,
                col.getEnabled(),
                rec.recommended(),
                rec.reason(),
                col.getCardinality(),
                Boolean.TRUE.equals(col.getCardinalityWarning()),
                allowedOperatorsFor(dataType),
                enumValues
        );
    }
    private Map<String, List<String>> fetchEnumValues(Connection connection, List<FilterableColumn> cols) {
        List<String> targets = cols.stream()
                .filter(c -> c.getCardinality() != null
                        && c.getCardinality() <= ENUM_CARDINALITY_THRESHOLD
                        && !Boolean.TRUE.equals(c.getCardinalityWarning()))
                .map(FilterableColumn::getColumnName)
                .toList();
        return connectionGateway.fetchDistinctColumnValues(
                connection, targets, connection.getUserTableSchema(), connection.getUserTableName(), ENUM_VALUES_LIMIT);
    }

    private static FilterContextResponse.RecipientSourceInfo recipientSource(Connection connection) {
        boolean hasSeparateEmailTable = connection.getEmailTableName() != null
                && !connection.getEmailTableName().equals(connection.getUserTableName());
        if (hasSeparateEmailTable) {
            return new FilterContextResponse.RecipientSourceInfo(
                    connection.getEmailTableSchema(), connection.getEmailTableName(), connection.getEmailColumn());
        }
        return new FilterContextResponse.RecipientSourceInfo(
                connection.getUserTableSchema(), connection.getUserTableName(), connection.getEmailColumn());
    }

    static String resolveDataType(String pgType, Integer cardinality, Boolean cardinalityWarning) {
        if (pgType == null) return "text";
        return switch (pgType.toLowerCase()) {
            case TYPE_BOOLEAN, "bool" -> TYPE_BOOLEAN;
            case "date", "timestamp", "timestamp without time zone",
                 "timestamp with time zone", "timestamptz" -> TYPE_DATETIME;
            case "integer", "int2", "int4", "int8", "bigint", "smallint",
                 "numeric", "float4", "float8", "real", "double precision", "decimal" -> TYPE_NUMBER;
            default -> {
                boolean looksLikeEnum = cardinality != null
                        && cardinality <= ENUM_CARDINALITY_THRESHOLD
                        && !Boolean.TRUE.equals(cardinalityWarning);
                yield looksLikeEnum ? TYPE_ENUM : TYPE_TEXT;
            }
        };
    }

    static String prettify(String columnName) {
        if (columnName == null || columnName.isBlank()) return columnName;
        String[] parts = columnName.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            if (i == 0) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
                sb.append(parts[i].substring(1).toLowerCase());
            } else {
                sb.append(' ');
                sb.append(parts[i].toLowerCase());
            }
        }
        return sb.toString();
    }

    private static List<String> allowedOperatorsFor(String dataType) {
        return switch (dataType) {
            case TYPE_ENUM     -> List.of(OP_EQUALS, OP_NOT_EQUALS, "is_any_of");
            case TYPE_NUMBER   -> List.of(OP_EQUALS, OP_NOT_EQUALS, "greater_than", "less_than");
            case TYPE_BOOLEAN  -> List.of(OP_EQUALS, OP_NOT_EQUALS);
            case TYPE_DATETIME -> List.of("after", "before", "within", "older");
            default            -> List.of(OP_EQUALS, OP_NOT_EQUALS, "contains", "starts_with", "is_empty", "is_not_empty");
        };
    }
}
