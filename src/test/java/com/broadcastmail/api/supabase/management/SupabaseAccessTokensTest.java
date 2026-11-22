package com.broadcastmail.api.supabase.management;

import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.common.exceptions.SupabaseReauthRequiredException;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.oauth.token.OAuthToken;
import com.broadcastmail.api.oauth.token.OAuthTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SupabaseAccessTokensTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef";

    private final OAuthTokenRepository repository = mock(OAuthTokenRepository.class);
    private final SupabaseManagementClient client = mock(SupabaseManagementClient.class);
    private final SupabaseAccessTokens tokens =
            new SupabaseAccessTokens(repository, client, new EncryptionProperties(KEY));

    private static final UUID ACCOUNT_ID = UUID.randomUUID();

    private void storedToken(OffsetDateTime expiresAt) {
        when(repository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.of(OAuthToken.builder()
                .accessToken(SecurityUtil.encrypt("access", KEY))
                .refreshToken(SecurityUtil.encrypt("refresh", KEY))
                .expiresAt(expiresAt)
                .build()));
    }

    @Test
    void shouldReturnStoredTokenWhileValid() {
        // Given
        storedToken(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));

        // When / Then
        assertThat(tokens.forAccount(ACCOUNT_ID)).isEqualTo("access");
        verifyNoInteractions(client);
    }

    @Test
    void shouldAskForReconnectWhenRefreshIsRejected() {
        // Given
        storedToken(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        when(client.refreshAccessToken(any())).thenThrow(HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "invalid_grant", null, null, null));

        // When / Then
        assertThatThrownBy(() -> tokens.forAccount(ACCOUNT_ID)).isInstanceOf(SupabaseReauthRequiredException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldNotAskForReconnectOnSupabaseOutage() {
        // Given
        storedToken(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        when(client.refreshAccessToken(any())).thenThrow(HttpServerErrorException.create(
                HttpStatus.BAD_GATEWAY, "down", null, null, null));

        // When / Then
        assertThatThrownBy(() -> tokens.forAccount(ACCOUNT_ID)).isInstanceOf(HttpServerErrorException.class);
    }
}
