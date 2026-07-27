package com.broadcastemail.api.common.exceptions;

public class CampaignNotFoundException extends RuntimeException {
    public CampaignNotFoundException() {
        super("Campaign not found");
    }
}
