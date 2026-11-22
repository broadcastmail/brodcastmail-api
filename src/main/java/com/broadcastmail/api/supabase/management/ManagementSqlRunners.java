package com.broadcastmail.api.supabase.management;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Hands out {@link ManagementSqlRunner}s for a project, so callers that only need to run privileged SQL don't have
 * to know how access tokens are looked up, refreshed or decrypted.
 */
@Component
@RequiredArgsConstructor
public class ManagementSqlRunners {

    private final SupabaseManagementClient supabaseManagementClient;
    private final SupabaseAccessTokens supabaseAccessTokens;

    /** Uses the account's stored OAuth token, refreshing it if expired. */
    public ManagementSqlRunner forAccount(UUID accountId, String projectRef) {
        return supabaseManagementClient.sqlRunner(supabaseAccessTokens.forAccount(accountId), projectRef);
    }

    /** Uses an encrypted access token carried outside the token store (e.g. by an onboarding session). */
    public ManagementSqlRunner forEncryptedToken(String encryptedAccessToken, String projectRef) {
        return supabaseManagementClient.sqlRunner(supabaseAccessTokens.decrypt(encryptedAccessToken), projectRef);
    }
}
