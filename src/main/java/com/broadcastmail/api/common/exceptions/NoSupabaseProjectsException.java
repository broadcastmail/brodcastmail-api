package com.broadcastmail.api.common.exceptions;

public class NoSupabaseProjectsException extends RuntimeException {
    public NoSupabaseProjectsException() {
        super("No Supabase projects found on this account. " +
                "Create a project at supabase.com first.");
    }
}
