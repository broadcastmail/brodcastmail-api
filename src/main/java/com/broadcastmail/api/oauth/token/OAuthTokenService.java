package com.broadcastmail.api.oauth.token;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuthTokenService {

    private final OAuthTokenRepository oAuthTokenRepository;

    /** Swaps the account's stored Supabase tokens for a freshly issued (already encrypted) pair. */
    @Transactional
    public void replace(UUID accountId, String encryptedAccessToken, String encryptedRefreshToken, Instant expiresAt) {
        OAuthToken token = oAuthTokenRepository.findByAccountId(accountId).orElseThrow();
        token.setAccessToken(encryptedAccessToken);
        token.setRefreshToken(encryptedRefreshToken);
        token.setExpiresAt(OffsetDateTime.ofInstant(expiresAt, ZoneId.systemDefault()));
        oAuthTokenRepository.save(token);
    }
}
