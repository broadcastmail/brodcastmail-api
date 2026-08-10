package com.broadcastmail.api.common.exceptions;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(UUID campaignId) {
        super("User not found with an ID: " + campaignId);
    }
}
