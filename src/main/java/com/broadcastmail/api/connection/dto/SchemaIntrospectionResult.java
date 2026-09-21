package com.broadcastmail.api.connection.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * Outcome of {@code SchemaIntrospectionService.introspect()}. There is no
 * "detected, but with some fields missing" state — either exactly one table
 * with a FK to {@code auth.users.id} was found (and everything below is
 * populated), several were found (and the caller must pick one), or none
 * were (and there is nothing else to report). Modelled as a sealed type
 * rather than a single record with nullable fields so callers can't
 * construct or read an inconsistent combination, and so the "not detected"
 * case can't be silently mistaken for a real result — see the {@code auth.users}
 * fallback this replaced.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "status")
@JsonSubTypes({
        @JsonSubTypes.Type(value = SchemaIntrospectionResult.Detected.class, name = "DETECTED"),
        @JsonSubTypes.Type(value = SchemaIntrospectionResult.MultipleCandidates.class, name = "MULTIPLE_CANDIDATES"),
        @JsonSubTypes.Type(value = SchemaIntrospectionResult.NotDetected.class, name = "NOT_DETECTED")
})
public sealed interface SchemaIntrospectionResult
        permits SchemaIntrospectionResult.Detected, SchemaIntrospectionResult.MultipleCandidates,
        SchemaIntrospectionResult.NotDetected {

    record Detected(
            String userTableName,
            String userTableSchema,
            String userIdColumn,
            List<DetectedColumn> filterableColumns,
            /**
             * Flat Supabase Auth metadata columns (e.g. last_sign_in_at,
             * email_confirmed_at) — always sourced from auth.users, independent of
             * whichever table this candidate is. Same list on every candidate.
             */
            List<DetectedColumn> authColumns
    ) implements SchemaIntrospectionResult {
    }

    /**
     * More than one table has a FK to {@code auth.users.id} — each candidate
     * is fully introspected (same shape as {@link Detected}) so the caller
     * can show the same table view for each one and let the user pick.
     */
    record MultipleCandidates(List<Detected> candidates) implements SchemaIntrospectionResult {
    }

    /**
     * No table with a FK to {@code auth.users.id} was found. There is
     * currently no way to pick a table manually — see docs/ONBOARDING.md.
     */
    record NotDetected() implements SchemaIntrospectionResult {
    }
}
