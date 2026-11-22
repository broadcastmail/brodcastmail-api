package com.broadcastmail.api.common.exceptions;

/** The tenant reader role could not log in through the pooler, even after retrying. */
public class ReaderNotReadyException extends RuntimeException {
    public ReaderNotReadyException(String message, Throwable cause) {
        super(message, cause);
    }
}
