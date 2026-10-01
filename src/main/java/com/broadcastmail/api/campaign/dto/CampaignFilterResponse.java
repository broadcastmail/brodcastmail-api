package com.broadcastmail.api.campaign.dto;

import com.broadcastmail.common.campaign.filter.CampaignFilter;
import com.broadcastmail.common.campaign.filter.FilterOperator;
import com.broadcastmail.common.campaign.filter.FilterSource;

public record CampaignFilterResponse(
        String columnName,
        FilterOperator operator,
        String filterValue,
        FilterSource source,
        String jsonKey
) {
    public static CampaignFilterResponse from(CampaignFilter filter) {
        return new CampaignFilterResponse(
                filter.getColumnName(),
                filter.getOperator(),
                filter.getFilterValue(),
                filter.getSource(),
                filter.getJsonKey()
        );
    }
}
