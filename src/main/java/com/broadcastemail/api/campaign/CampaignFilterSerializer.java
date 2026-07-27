package com.broadcastemail.api.campaign;

import com.broadcastemail.api.campaign.filter.CampaignFilter;
import com.broadcastemail.api.campaign.filter.FilterOperator;
import com.broadcastemail.api.campaign.filter.FilterQuery;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CampaignFilterSerializer {
    public FilterQuery serialize(List<CampaignFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return new FilterQuery("", List.of());
        }

        String fragments = filters.stream().sorted(Comparator.comparingInt(CampaignFilter::getFilterOrder))
                .map(this::buildFragment)
                .collect(Collectors.joining(" AND "));
        String sql = "WHERE " + fragments;

        List<Object> parameters = filters.stream()
                .sorted(Comparator.comparingInt(CampaignFilter::getFilterOrder))
                .map(f -> f.getOperator() == FilterOperator.CONTAINS
                        ? "%" + f.getFilterValue() + "%"
                        : f.getFilterValue())
                .collect(Collectors.toList());
        return new FilterQuery(sql, parameters);
    }

    private String buildFragment(CampaignFilter filter) {
        if (!filter.getColumnName().matches("[a-zA-Z_]\\w*")) {
            throw new IllegalArgumentException("Invalid column name: " + filter.getColumnName());
        }
        String operator = switch (filter.getOperator()) {
            case EQ -> "=";
            case NEQ -> "!=";
            case GT -> ">";
            case LT -> "<";
            case CONTAINS -> "ILIKE";
        };
        return "\"" + filter.getColumnName() + "\" " + operator + " ?";
    }
}
