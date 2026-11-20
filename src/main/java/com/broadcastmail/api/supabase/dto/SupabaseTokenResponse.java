package com.broadcastmail.api.supabase.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = false)
public class SupabaseTokenResponse {
    @JsonProperty("access_token") private String accessToken;
    @JsonProperty("refresh_token") private String refreshToken;
    @JsonProperty("expires_in") private int expiresIn;
    @JsonProperty("token_type") private String tokenType;
    private final Map<String, Object> extraFields = new HashMap<>();

    @JsonAnySetter
    public void setExtraField(String key, Object value) { extraFields.put(key, value); }

    public String accessToken() { return accessToken; }
    public String refreshToken() { return refreshToken; }
    public int expiresIn() { return expiresIn; }
    public String tokenType() { return tokenType; }
    public Set<String> extraFieldNames() { return extraFields.keySet(); }
    public Object extraField(String key) { return extraFields.get(key); }
}
