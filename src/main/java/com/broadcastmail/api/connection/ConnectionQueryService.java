package com.broadcastmail.api.connection;

import com.broadcastmail.api.common.exceptions.ConnectionNotFoundException;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.oauth.OAuthToken;
import com.broadcastmail.api.oauth.dto.ProjectOption;
import com.broadcastmail.api.supabase.SupabaseManagementClient;
import com.broadcastmail.api.supabase.dto.SupabaseTokenResponse;
import com.broadcastmail.api.token.OAuthTokenRepository;
import com.broadcastmail.api.common.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConnectionQueryService {

    private final OAuthTokenRepository oAuthTokenRepository;
    private final SupabaseManagementClient supabaseManagementClient;
    private final EncryptionProperties encryptionProperties;
    public List<ProjectOption> listProjects(UUID accountId) {
        OAuthToken token = oAuthTokenRepository.findByAccountId(accountId)
                .orElseThrow(ConnectionNotFoundException::new);

        String rawAccessToken = resolveAccessToken(token);

        return supabaseManagementClient.listProjects(rawAccessToken).stream()
                .map(p -> new ProjectOption(p.ref(), p.name(), p.status(), null))
                .toList();
    }

    private String resolveAccessToken(OAuthToken token) {
        String rawAccessToken = SecurityUtil.decrypt(
                token.getAccessToken(), encryptionProperties.key());

        if (token.getExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            String rawRefreshToken = SecurityUtil.decrypt(
                    token.getRefreshToken(), encryptionProperties.key());
            SupabaseTokenResponse refreshed = supabaseManagementClient
                    .refreshAccessToken(rawRefreshToken);
            rawAccessToken = refreshed.accessToken();
            token.setAccessToken(SecurityUtil.encrypt(rawAccessToken, encryptionProperties.key()));
            token.setRefreshToken(SecurityUtil.encrypt(
                    refreshed.refreshToken(), encryptionProperties.key()));
            token.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC)
                    .plusSeconds(refreshed.expiresIn()));
            oAuthTokenRepository.save(token);
        }

        return rawAccessToken;
    }
}