package com.broadcastmail.api.common.exceptions;

public class InvalidUnsubscribeTokenException extends RuntimeException {
    public InvalidUnsubscribeTokenException() {
        super("Invalid unsubscribe token");
    }
}
