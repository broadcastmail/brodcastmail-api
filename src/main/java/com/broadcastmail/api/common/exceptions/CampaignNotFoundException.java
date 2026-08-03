package com.broadcastmail.api.common.exceptions;

import java.util.UUID;

public class CampaignNotFoundException extends RuntimeException {
    public CampaignNotFoundException(UUID campaignId) {
        super("Campaign not found with an ID: " + campaignId);
    }
}
