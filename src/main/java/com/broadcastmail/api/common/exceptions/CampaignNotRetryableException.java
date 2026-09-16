package com.broadcastmail.api.common.exceptions;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CampaignNotRetryableException extends RuntimeException {
    public CampaignNotRetryableException(String message) {
        super("Campaign is not in a retryable state");
    }
}
