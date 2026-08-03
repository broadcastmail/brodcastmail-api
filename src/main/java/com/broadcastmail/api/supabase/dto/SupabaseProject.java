package com.broadcastmail.api.supabase.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SupabaseProject(String ref,
                              String name,
                              String status,
                              @JsonProperty("created_at") String createdAt) {
}
