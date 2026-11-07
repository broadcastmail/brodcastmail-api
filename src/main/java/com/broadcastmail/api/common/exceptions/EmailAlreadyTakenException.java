package com.broadcastmail.api.common.exceptions;

public class EmailAlreadyTakenException extends RuntimeException {
    public EmailAlreadyTakenException() {
        super("This Supabase account is already linked to another Broadcastmail account");
    }
}
