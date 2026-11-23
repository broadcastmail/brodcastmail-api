package com.broadcastmail.api.campaign.filter;

import com.broadcastmail.api.campaign.filter.dto.FilterRequest;
import com.broadcastmail.common.campaign.filter.CampaignFilter;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

public final class FilterRequestMapper {

    private FilterRequestMapper() {}

    public static CampaignFilter toEntity(UUID campaignId, int order, FilterRequest request) {
        String filterValue = request.filterValues() != null && !request.filterValues().isEmpty()
                ? String.join(",", request.filterValues())
                : request.filterValue();
        return CampaignFilter.builder()
                .campaignId(campaignId)
                .columnName(request.columnName())
                .operator(request.operator())
                .filterValue(filterValue)
                .filterOrder(order)
                .source(request.sourceOrDefault())
                .jsonKey(request.jsonKey())
                .groupIndex(request.groupIndex())
                .groupLogic(request.groupLogic())
                .build();
    }

    /** Unsaved filters (e.g. the audience preview), in request order. */
    public static List<CampaignFilter> toEntities(List<FilterRequest> requests) {
        return IntStream.range(0, requests.size()).mapToObj(i -> toEntity(null, i, requests.get(i))).toList();
    }
}
