package com.broadcastmail.api.common.exceptions;

public class CampaignNotRetryableException extends RuntimeException {
    public CampaignNotRetryableException() {
        super("Campaign is not in a retryable state");
    }
}
