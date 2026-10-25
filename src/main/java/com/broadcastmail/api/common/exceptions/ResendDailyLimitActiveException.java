package com.broadcastmail.api.common.exceptions;

public class ResendDailyLimitActiveException extends RuntimeException {
    public ResendDailyLimitActiveException() {
        super("RESEND_RATE_LIMITED");
    }
}
