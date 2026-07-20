package com.broadcastemail.api.common.exceptions;

public class InvalidResendApiKeyException extends RuntimeException {
    public InvalidResendApiKeyException(String message) {
        super(message);
    }
}
