package com.broadcastmail.api.supabase.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SupabaseProject(String ref,
                              String name,
                              String status,
                              @JsonProperty("created_at") String createdAt,
                              String region,
                              @JsonProperty("cloud_provider") String cloudProvider) {

    public String poolerPrefix() {
        String provider = cloudProvider != null ? cloudProvider.toLowerCase() : "aws";
        return provider + "-0-" + region;
    }
}
