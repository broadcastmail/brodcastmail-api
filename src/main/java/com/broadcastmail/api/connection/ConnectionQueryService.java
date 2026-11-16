package com.broadcastmail.api.connection;

import com.broadcastmail.api.common.exceptions.ConnectionNotFoundException;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.connection.dto.FilterableColumnResponse;
import com.broadcastmail.api.connection.dto.ReconnectCompatibilityResponse;
import com.broadcastmail.api.connection.dto.SupabaseIntrospectionData;
import com.broadcastmail.api.oauth.OAuthToken;
import com.broadcastmail.api.oauth.dto.ProjectOption;
import com.broadcastmail.api.supabase.SupabaseManagementClient;
import com.broadcastmail.api.supabase.SupabaseSql;
import com.broadcastmail.api.supabase.dto.SupabaseTokenResponse;
import com.broadcastmail.api.token.OAuthTokenRepository;
import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.filterablecolumn.FilterableColumn;
import com.broadcastmail.common.filterablecolumn.FilterableColumnRepository;
import com.broadcastmail.common.connection.ConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConnectionQueryService {

    private final OAuthTokenRepository oAuthTokenRepository;
    private final SupabaseManagementClient supabaseManagementClient;
    private final EncryptionProperties encryptionProperties;
    private final ConnectionRepository connectionRepository;
    private final FilterableColumnRepository filterableColumnRepository;
    private final SupabaseIntrospectionService supabaseIntrospectionService;

    public List<FilterableColumnResponse> listColumns(UUID accountId) {
        var connection = connectionRepository.findByAccountId(accountId)
                .orElseThrow(ConnectionNotFoundException::new);
        return filterableColumnRepository.findByConnectionId(connection.getId()).stream()
                .map(FilterableColumnResponse::from)
                .toList();
    }

    public ReconnectCompatibilityResponse checkCompatibility(UUID accountId, String projectRef) {
        Connection connection = connectionRepository.findByAccountId(accountId)
                .orElseThrow(ConnectionNotFoundException::new);

        String jdbcUrl = SupabaseSql.buildJdbcUrl(projectRef);
        SupabaseIntrospectionData data = supabaseIntrospectionService.introspect(jdbcUrl, connection.getEncryptedCreds());

        String userKey = connection.getUserTableSchema() + "." + connection.getUserTableName();
        List<SupabaseIntrospectionData.Column> userCols = data.columnsByTable().get(userKey);
        boolean userTableOk = userCols != null;

        String emailKey = connection.getEmailTableSchema() != null
                ? connection.getEmailTableSchema() + "." + connection.getEmailTableName()
                : userKey;
        List<SupabaseIntrospectionData.Column> emailCols = data.columnsByTable().get(emailKey);
        boolean emailColumnOk = emailCols != null && emailCols.stream()
                .anyMatch(c -> c.name().equals(connection.getEmailColumn()));

        Set<String> availableNames = emailCols != null
                ? emailCols.stream().map(SupabaseIntrospectionData.Column::name).collect(Collectors.toSet())
                : Set.of();
        List<FilterableColumn> filterCols = filterableColumnRepository.findByConnectionId(connection.getId());
        List<String> missing = filterCols.stream()
                .map(FilterableColumn::getColumnName)
                .filter(name -> !availableNames.contains(name))
                .toList();

        return new ReconnectCompatibilityResponse(userTableOk, emailColumnOk, missing);
    }

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