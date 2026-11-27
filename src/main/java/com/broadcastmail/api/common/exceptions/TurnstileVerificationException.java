package com.broadcastmail.api.common.exceptions;

public class TurnstileVerificationException extends RuntimeException {
    public TurnstileVerificationException() {
        super("Couldn't verify you're human. Reload the page and try again");
    }
}
