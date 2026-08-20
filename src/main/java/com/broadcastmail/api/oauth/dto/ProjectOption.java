package com.broadcastmail.api.oauth.dto;

/**
 * One project on the /onboarding/select-project picker.
 *
 * userCount is best-effort: null when the project isn't ACTIVE_HEALTHY
 * (e.g. paused/restoring — querying it would just fail) or when the count
 * query itself failed. A missing count is expected and not an error state —
 * unlike {@code SchemaIntrospectionResult}, this isn't a "which shape is
 * this" split, just one metric on an otherwise-uniform row that sometimes
 * can't be filled in.
 */
public record ProjectOption(
        String ref,
        String name,
        String status,
        Integer userCount
) {
}
