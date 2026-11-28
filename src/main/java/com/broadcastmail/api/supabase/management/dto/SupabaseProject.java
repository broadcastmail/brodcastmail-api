package com.broadcastmail.api.supabase.management.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SupabaseProject(String ref,
                              String name,
                              String status,
                              @JsonProperty("created_at") String createdAt,
                              String region,
                              @JsonProperty("cloud_provider") String cloudProvider) {
}
