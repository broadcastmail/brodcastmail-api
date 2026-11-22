package com.broadcastmail.api.common.exceptions;

public class CampaignContentMissingException extends RuntimeException {
    public CampaignContentMissingException() {
        super("Add a subject and email body before sending");
    }
}
