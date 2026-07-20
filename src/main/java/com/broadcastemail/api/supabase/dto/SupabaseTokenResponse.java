package com.broadcastemail.api.supabase.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SupabaseTokenResponse(
        @JsonProperty("access_token")String accessToken,
        @JsonProperty("refresh_token")String refreshToken,
        @JsonProperty("expires_in")int expiresIn,
        @JsonProperty("token_type")String tokenType
) {
}
