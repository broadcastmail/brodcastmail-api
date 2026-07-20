package com.broadcastemail.api.common.exceptions;

public class OAuthStateValidationException extends RuntimeException {
    public OAuthStateValidationException() {
        super("Invalid or expired OAuth state parameter");
    }
}
