package com.broadcastmail.api.common.exceptions;

/**
 * The stored Supabase OAuth token can't be refreshed (access revoked, or refresh token expired/already used),
 * so the user has to reconnect Supabase through OAuth. The message is a stable code the frontend matches on.
 */
public class SupabaseReauthRequiredException extends RuntimeException {
    public static final String CODE = "SUPABASE_REAUTH_REQUIRED";

    public SupabaseReauthRequiredException(Throwable cause) {
        super(CODE, cause);
    }
}
