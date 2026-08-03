package com.broadcastmail.api.common.exceptions;

public class ResendRateLimitException extends RuntimeException {
    public ResendRateLimitException(String s) {
        super(s);
    }
}
