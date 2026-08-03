package com.broadcastmail.api.campaign.dto;

import java.time.OffsetDateTime;
import java.util.Optional;

public record UpdateCampaignRequest(

        Optional<String> name,
        Optional<String> subject,
        Optional<String> bodyHtml,
        Optional<OffsetDateTime> scheduledAt
) {}
