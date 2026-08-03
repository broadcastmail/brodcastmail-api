package com.broadcastmail.api.campaign;


import com.broadcastmail.api.campaign.filter.CampaignFilter;
import com.broadcastmail.api.campaign.filter.FilterOperator;
import com.broadcastmail.api.campaign.filter.FilterQuery;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CampaignFilterSerializerTest {

    private CampaignFilterSerializer serializer = new CampaignFilterSerializer();

    private CampaignFilter filter(String columnName, FilterOperator operator, String value, int order) {
        return CampaignFilter.builder()
                .columnName(columnName)
                .operator(operator)
                .filterValue(value)
                .filterOrder(order)
                .build();
    }

    @Test
    void shouldReturnEmptySqlWhenNoFilters() {
        // Given/When
        FilterQuery result = serializer.serialize(List.of());

        // Then
        assertThat(result.sql()).isEmpty();
        assertThat(result.parameters()).isEmpty();
    }

    @Test
    void shouldBuildWhereClauseWithSingleFilter() {
        // Given
        List<CampaignFilter> filters = List.of(filter("plan", FilterOperator.EQ, "free", 0));

        // When
        FilterQuery result = serializer.serialize(filters);

        // Then
        assertThat(result.sql()).isEqualTo("WHERE \"plan\" = ?");
        assertThat(result.parameters()).containsExactly("free");
    }

    @Test
    void shouldJoinMultipleFiltersWithAnd() {
        List<CampaignFilter> filters = List.of(
                filter("plan", FilterOperator.EQ, "free", 0),
                filter("created_at", FilterOperator.GT, "2026-01-01", 1)
        );

        // When
        FilterQuery result = serializer.serialize(filters);

        // Then
        assertThat(result.sql()).isEqualTo("WHERE \"plan\" = ? AND \"created_at\" > ?");
        assertThat(result.parameters()).isEqualTo(List.of("free", "2026-01-01"));
    }


    @Test
    void shouldWrapContainsValueWithWildcards() {
        // Given
        List<CampaignFilter> filters = List.of(filter("full_name", FilterOperator.CONTAINS, "premium", 0));

        // When
        FilterQuery result = serializer.serialize(filters);

        // Then
        assertThat(result.sql()).isEqualTo("WHERE \"full_name\" ILIKE ?");
        assertThat(result.parameters()).containsExactly("%premium%");
    }

    @Test
    void shouldRejectColumnNameWithSpecialCharacters()
    {
        List<CampaignFilter> filters = List.of(filter("plan; DROP TABLE users", FilterOperator.EQ, "free", 0));

        assertThatThrownBy(() -> serializer.serialize(filters))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldOrderFiltersByFilterOrder()
    {
        List<CampaignFilter> filters = List.of(
                filter("created_at", FilterOperator.GT, "2024-01-01", 1),
                filter("plan", FilterOperator.EQ, "free", 0)
        );

        FilterQuery result = serializer.serialize(filters);
        assertThat(result.sql()).isEqualTo("WHERE \"plan\" = ? AND \"created_at\" > ?");
        assertThat(result.parameters()).containsExactly("free", "2024-01-01");
    }
}