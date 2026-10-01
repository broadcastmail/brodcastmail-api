package com.broadcastmail.api.common.exceptions;

import java.util.List;

public class InvalidCampaignFilterException extends RuntimeException {
    public InvalidCampaignFilterException(List<String> problems) {
        super("Invalid campaign filters: " + String.join("; ", problems));
    }
}
