package com.broadcastmail.api.supabase;

import com.broadcastmail.api.campaign.filter.dto.FilterRequest;
import com.broadcastmail.common.campaign.filter.CampaignFilterSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class TenantFilterSqlBuilder {

    private TenantFilterSqlBuilder() {}

    public record FilterClause(String sql, List<Object> params) {}

    public static FilterClause buildWhereClause(List<FilterRequest> filters, String rootLogic) {
        if (filters.isEmpty()) return new FilterClause("", List.of());

        String root = rootLogic != null && rootLogic.equalsIgnoreCase("OR") ? "OR" : "AND";

        List<FilterRequest> rootConditions = filters.stream()
                .filter(f -> f.groupIndex() == null)
                .toList();

        Map<Integer, List<FilterRequest>> groups = filters.stream()
                .filter(f -> f.groupIndex() != null)
                .collect(Collectors.groupingBy(FilterRequest::groupIndex,
                        java.util.LinkedHashMap::new, Collectors.toList()));

        List<String> fragments = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();

        for (FilterRequest f : rootConditions) {
            fragments.add(buildFilterFragment(f));
            parameters.addAll(buildConditionParams(f));
        }

        for (List<FilterRequest> group : groups.values()) {
            String groupLogic = group.get(0).groupLogic() != null ? group.get(0).groupLogic().toUpperCase() : "AND";
            List<String> groupFragments = group.stream().map(TenantFilterSqlBuilder::buildFilterFragment).toList();
            group.forEach(f -> parameters.addAll(buildConditionParams(f)));
            fragments.add("(" + String.join(" " + groupLogic + " ", groupFragments) + ")");
        }

        return new FilterClause("WHERE " + String.join(" " + root + " ", fragments), parameters);
    }

    public static FilterClause buildWhereClause(List<FilterRequest> filters) {
        return buildWhereClause(filters, "AND");
    }

    public static List<Object> buildFilterParams(List<FilterRequest> filters) {
        return buildWhereClause(filters).params();
    }

    private static List<Object> buildConditionParams(FilterRequest filter) {
        return switch (filter.operator()) {
            case IS_EMPTY, IS_NOT_EMPTY -> List.of();
            case IS_ANY_OF -> filter.filterValues() != null ? new ArrayList<>(filter.filterValues()) : List.of();
            case CONTAINS, STARTS_WITH -> List.of(filter.filterValue());
            case WITHIN, OLDER -> {
                try { yield List.of(Integer.parseInt(filter.filterValue().trim())); }
                catch (NumberFormatException _) { throw new IllegalArgumentException("Expected integer value: " + filter.filterValue()); }
            }
            default -> filter.filterValue() != null ? List.of(filter.filterValue()) : List.of();
        };
    }

    private static String buildFilterFragment(FilterRequest filter) {
        SupabaseSql.requireIdentifier(filter.columnName(), "column name");
        String col = switch (filter.sourceOrDefault()) {
            case PROFILE_TABLE, AUTH_METADATA -> "\"" + filter.columnName() + "\"";
            case AUTH_METADATA_JSON -> {
                if (!CampaignFilterSerializer.JSON_METADATA_COLUMNS.contains(filter.columnName())) {
                    throw new IllegalArgumentException("Invalid metadata column: " + filter.columnName());
                }
                if (filter.jsonKey() == null || !SupabaseSql.IDENTIFIER_PATTERN.matcher(filter.jsonKey()).matches()) {
                    throw new IllegalArgumentException("Invalid metadata key: " + filter.jsonKey());
                }
                yield "\"" + filter.columnName() + "\"->>'" + filter.jsonKey() + "'";
            }
        };
        return switch (filter.operator()) {
            case EQ ->           col + " = ?";
            case NEQ ->          col + " != ?";
            case GT, AFTER ->    col + " > ?";
            case LT, BEFORE ->   col + " < ?";
            case CONTAINS ->     col + " ILIKE '%' || ? || '%'";
            case STARTS_WITH ->  col + " ILIKE ? || '%'";
            case IS_EMPTY ->     "(" + col + " IS NULL OR " + col + " = '')";
            case IS_NOT_EMPTY -> "(" + col + " IS NOT NULL AND " + col + " != '')";
            case WITHIN ->       col + " > NOW() - INTERVAL '1 day' * ?";
            case OLDER ->        col + " < NOW() - INTERVAL '1 day' * ?";
            case IS_ANY_OF -> {
                int count = filter.filterValues() != null ? filter.filterValues().size() : 0;
                String placeholders = "?,".repeat(count);
                yield col + " IN (" + (count > 0 ? placeholders.substring(0, placeholders.length() - 1) : "NULL") + ")";
            }
        };
    }
}
