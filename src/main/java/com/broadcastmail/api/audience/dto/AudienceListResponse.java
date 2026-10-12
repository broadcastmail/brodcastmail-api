package com.broadcastmail.api.audience.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AudienceListResponse(
        List<AudienceRecipient> recipients,
        String nextCursor,
        boolean hasMore,
        long total
) {
    public record AudienceRecipient(
            UUID id,
            String email,
            Map<String, Object> attributes
    ) {}
}
