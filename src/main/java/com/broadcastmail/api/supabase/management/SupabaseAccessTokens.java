package com.broadcastmail.api.supabase.management;

import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.common.exceptions.ConnectionNotFoundException;
import com.broadcastmail.api.common.exceptions.SupabaseReauthRequiredException;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.oauth.token.OAuthToken;
import com.broadcastmail.api.supabase.management.dto.SupabaseTokenResponse;
import com.broadcastmail.api.oauth.token.OAuthTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Resolves a raw Supabase Management API access token for an onboarding session or a connected account. */
@Component
@RequiredArgsConstructor
public class SupabaseAccessTokens {

    private final OAuthTokenRepository oAuthTokenRepository;
    private final SupabaseManagementClient supabaseManagementClient;
    private final EncryptionProperties encryptionProperties;


    /** Decrypts an access token carried by an onboarding session (it is refreshed at OAuth time, not here). */
    public String decrypt(String encryptedAccessToken) {
        return SecurityUtil.decrypt(encryptedAccessToken, encryptionProperties.key());
    }

    public String forAccount(UUID accountId) {
        OAuthToken token = oAuthTokenRepository.findByAccountId(accountId)
                .orElseThrow(ConnectionNotFoundException::new);
        return resolve(token);
    }

    private String resolve(OAuthToken token) {
        String rawAccessToken = SecurityUtil.decrypt(token.getAccessToken(), encryptionProperties.key());

        if (token.getExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            String rawRefreshToken = SecurityUtil.decrypt(token.getRefreshToken(), encryptionProperties.key());
            SupabaseTokenResponse refreshed;
            try {
                refreshed = supabaseManagementClient.refreshAccessToken(rawRefreshToken);
            } catch (RestClientResponseException e) {
                // 4xx from the token endpoint: revoked grant or a refresh token that's expired / already rotated.
                if (e.getStatusCode().is4xxClientError()) throw new SupabaseReauthRequiredException(e);
                throw e;
            }
            rawAccessToken = refreshed.accessToken();
            token.setAccessToken(SecurityUtil.encrypt(rawAccessToken, encryptionProperties.key()));
            token.setRefreshToken(SecurityUtil.encrypt(refreshed.refreshToken(), encryptionProperties.key()));
            token.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(refreshed.expiresIn()));
            oAuthTokenRepository.save(token);
        }

        return rawAccessToken;
    }
}
