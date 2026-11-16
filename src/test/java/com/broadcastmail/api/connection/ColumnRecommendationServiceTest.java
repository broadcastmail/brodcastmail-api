package com.broadcastmail.api.connection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ColumnRecommendationServiceTest {

    @Test
    void shouldRecommendRecognizedSegmentationColumn() {
        var result = ColumnRecommendationService.recommend("plan", "text");
        assertThat(result.recommended()).isTrue();
        assertThat(result.reason()).isNotBlank();
    }

    @Test
    void shouldNotRecommendWhenTypeDoesNotMatchTheConcept() {
        var result = ColumnRecommendationService.recommend("plan", "number");
        assertThat(result.recommended()).isFalse();
        assertThat(result.reason()).isNull();
    }

    @Test
    void shouldNotRecommendUnrecognizedColumn() {
        assertThat(ColumnRecommendationService.recommend("internal_tracking_id", "text").recommended()).isFalse();
    }

    @Test
    void shouldRankMoreImportantConceptsHigher() {
        int subscriptionRank = ColumnRecommendationService.recommend("plan", "text").rank();
        int activityRank = ColumnRecommendationService.recommend("last_sign_in_at", "datetime").rank();
        assertThat(subscriptionRank).isLessThan(activityRank);
    }
}
