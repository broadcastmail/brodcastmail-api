package com.broadcastmail.api.campaign.filter.dto;

import com.broadcastmail.common.campaign.filter.FilterOperator;
import com.broadcastmail.common.campaign.filter.FilterSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * columnName means different things depending on source: the profile-table or
 * auth.users column name for PROFILE_TABLE/AUTH_METADATA, or the jsonb column
 * (raw_user_meta_data / raw_app_meta_data) for AUTH_METADATA_JSON, where jsonKey
 * then names the key inside it. jsonKey is required only for AUTH_METADATA_JSON -
 * there's no catalog of valid keys to validate against, since jsonb keys aren't
 * introspectable, so the caller is trusted to name a real one. source defaults to
 * PROFILE_TABLE when omitted, for callers written before this field existed.
 */
public record FilterRequest(
        @NotBlank
        String columnName,
        @NotNull
        FilterOperator operator,
        @NotBlank
        String filterValue,
        FilterSource source,
        String jsonKey
) {
    public FilterSource sourceOrDefault() {
        return source != null ? source : FilterSource.PROFILE_TABLE;
    }
}
