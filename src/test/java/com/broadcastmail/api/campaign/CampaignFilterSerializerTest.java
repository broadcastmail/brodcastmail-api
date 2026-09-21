package com.broadcastmail.api.campaign;


import com.broadcastmail.common.campaign.filter.CampaignFilter;
import com.broadcastmail.common.campaign.filter.CampaignFilterSerializer;
import com.broadcastmail.common.campaign.filter.FilterOperator;
import com.broadcastmail.common.campaign.filter.FilterQuery;
import com.broadcastmail.common.campaign.filter.FilterSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CampaignFilterSerializerTest {

    private final CampaignFilterSerializer serializer = new CampaignFilterSerializer();

    private CampaignFilter filter(String columnName, FilterOperator operator, String value, int order) {
        return CampaignFilter.builder()
                .columnName(columnName)
                .operator(operator)
                .filterValue(value)
                .filterOrder(order)
                .source(FilterSource.PROFILE_TABLE)
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

    @Test
    void shouldBuildWhereClauseForAuthMetadataColumn() {
        CampaignFilter filter = CampaignFilter.builder()
                .columnName("email_confirmed_at")
                .operator(FilterOperator.NEQ)
                .filterValue("null")
                .filterOrder(0)
                .source(FilterSource.AUTH_METADATA)
                .build();

        FilterQuery result = serializer.serialize(List.of(filter));

        assertThat(result.sql()).isEqualTo("WHERE \"email_confirmed_at\" != ?");
    }

    @Test
    void shouldBuildJsonPathFragmentForAuthMetadataJson() {
        CampaignFilter filter = CampaignFilter.builder()
                .columnName("raw_user_meta_data")
                .operator(FilterOperator.EQ)
                .filterValue("referral")
                .filterOrder(0)
                .source(FilterSource.AUTH_METADATA_JSON)
                .jsonKey("signup_source")
                .build();

        FilterQuery result = serializer.serialize(List.of(filter));

        assertThat(result.sql()).isEqualTo("WHERE \"raw_user_meta_data\"->>'signup_source' = ?");
        assertThat(result.parameters()).containsExactly("referral");
    }

    @Test
    void shouldRejectAuthMetadataJsonWithDisallowedColumn() {
        CampaignFilter filter = CampaignFilter.builder()
                .columnName("plan")
                .operator(FilterOperator.EQ)
                .filterValue("pro")
                .filterOrder(0)
                .source(FilterSource.AUTH_METADATA_JSON)
                .jsonKey("plan")
                .build();
        List<CampaignFilter> filters = List.of(filter);

        assertThatThrownBy(() -> serializer.serialize(filters))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectAuthMetadataJsonWithInvalidKey() {
        CampaignFilter filter = CampaignFilter.builder()
                .columnName("raw_user_meta_data")
                .operator(FilterOperator.EQ)
                .filterValue("x")
                .filterOrder(0)
                .source(FilterSource.AUTH_METADATA_JSON)
                .jsonKey("bad key; --")
                .build();
        List<CampaignFilter> filters = List.of(filter);

        assertThatThrownBy(() -> serializer.serialize(filters))
                .isInstanceOf(IllegalArgumentException.class);
    }
}