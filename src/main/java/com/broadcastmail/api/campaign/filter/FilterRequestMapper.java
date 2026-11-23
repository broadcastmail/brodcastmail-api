package com.broadcastmail.api.campaign.filter;

import com.broadcastmail.api.campaign.filter.dto.FilterRequest;
import com.broadcastmail.common.campaign.filter.CampaignFilter;
import com.broadcastmail.common.campaign.filter.FilterOperator;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

public final class FilterRequestMapper {

    private static final Set<FilterOperator> NO_VALUE_OPERATORS = Set.of(FilterOperator.IS_EMPTY, FilterOperator.IS_NOT_EMPTY);

    private FilterRequestMapper() {}

    public static List<FilterRequest> withValues(List<FilterRequest> requests) {
        if (requests == null) return null;
        return requests.stream().filter(FilterRequestMapper::hasValue).toList();
    }

    private static boolean hasValue(FilterRequest request) {
        if (request.operator() == null || NO_VALUE_OPERATORS.contains(request.operator())) return true;
        if (request.filterValues() != null && request.filterValues().stream().anyMatch(v -> v != null && !v.isBlank())) {
            return true;
        }
        return request.filterValue() != null && !request.filterValue().isBlank();
    }

    public static CampaignFilter toEntity(UUID campaignId, int order, FilterRequest request) {
        String filterValue = request.filterValues() != null && !request.filterValues().isEmpty()
                ? String.join(",", request.filterValues())
                : request.filterValue();
        if (filterValue == null) filterValue = "";
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
