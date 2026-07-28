package com.broadcastemail.api.common.exceptions;

public class CampaignNotEditableException extends RuntimeException {
    public CampaignNotEditableException() {
        super("Campaign Not Editable");
    }

}
