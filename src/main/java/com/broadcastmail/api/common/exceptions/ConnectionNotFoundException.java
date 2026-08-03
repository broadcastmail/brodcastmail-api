package com.broadcastmail.api.common.exceptions;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class ConnectionNotFoundException extends RuntimeException {
    public ConnectionNotFoundException(String message) {
        super("Connection not found: " + message);
    }
}
