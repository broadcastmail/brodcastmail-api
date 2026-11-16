package com.broadcastmail.api.campaign.dto;

import com.broadcastmail.common.campaign.filter.CampaignFilter;
import com.broadcastmail.common.campaign.filter.FilterOperator;
import com.broadcastmail.common.campaign.filter.FilterSource;
import java.util.List;

public record CampaignFilterResponse(
        String columnName,
        FilterOperator operator,
        String filterValue,
        List<String> filterValues,
        FilterSource source,
        String jsonKey,
        Integer groupIndex,
        String groupLogic
) {
    public static CampaignFilterResponse from(CampaignFilter filter) {
        boolean isAnyOf = "IS_ANY_OF".equals(filter.getOperator().name());
        List<String> filterValues = isAnyOf && filter.getFilterValue() != null
                ? java.util.Arrays.stream(filter.getFilterValue().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
                : null;
        return new CampaignFilterResponse(
                filter.getColumnName(),
                filter.getOperator(),
                isAnyOf ? null : filter.getFilterValue(),
                filterValues,
                filter.getSource(),
                filter.getJsonKey(),
                filter.getGroupIndex(),
                filter.getGroupLogic()
        );
    }
}
