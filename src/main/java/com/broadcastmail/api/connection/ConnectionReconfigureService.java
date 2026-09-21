package com.broadcastmail.api.connection;

import com.broadcastmail.api.common.exceptions.ConnectionNotFoundException;
import com.broadcastmail.api.common.exceptions.InvalidOnboardingSessionException;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.filterablecolumn.FilterableColumn;
import com.broadcastmail.api.filterablecolumn.FilterableColumnFactory;
import com.broadcastmail.api.filterablecolumn.FilterableColumnRepository;
import com.broadcastmail.api.oauth.OAuthSessionStore;
import com.broadcastmail.api.oauth.OAuthToken;
import com.broadcastmail.api.onboarding.OnboardingSession;
import com.broadcastmail.api.token.OAuthTokenRepository;
import com.broadcastmail.common.campaign.filter.FilterSource;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConnectionReconfigureService {

    private final OAuthSessionStore onboardingSessionStore;
    private final ConnectionRepository connectionRepository;
    private final FilterableColumnRepository filterableColumnRepository;
    private final OAuthTokenRepository oAuthTokenRepository;
    private final EncryptionProperties encryptionProperties;

    @Transactional
    public void reconfigure(String sessionToken) {
        OnboardingSession session = onboardingSessionStore.get(sessionToken)
                .requireSchemaConfirmed();

        if (!session.isReconfigure()) {
            throw new InvalidOnboardingSessionException();
        }

        Connection connection = connectionRepository.findByAccountId(session.getAccountId())
                .orElseThrow(ConnectionNotFoundException::new);

        connection.setProjectRef(session.getProjectRef());
        connection.setProjectUrl(session.getProjectUrl());
        connection.setEncryptedCreds(session.getEncryptedRolePassword());
        connection.setUserTableSchema(session.getSchemaDetails().userSchema());
        connection.setUserTableName(session.getSchemaDetails().userTable());
        connection.setUserIdColumn(session.getSchemaDetails().userIdColumn());
        connectionRepository.save(connection);

        filterableColumnRepository.deleteByConnectionId(connection.getId());
        List<FilterableColumn> columns = new ArrayList<>();
        columns.addAll(FilterableColumnFactory.from(
                connection.getId(), session.getDetectedColumns(), FilterSource.PROFILE_TABLE, session.getConfirmedColumnNames()));
        columns.addAll(FilterableColumnFactory.from(
                connection.getId(), session.getAuthColumns(), FilterSource.AUTH_METADATA, session.getConfirmedColumnNames()));
        filterableColumnRepository.saveAll(columns);

        OAuthToken token = oAuthTokenRepository.findByAccountId(session.getAccountId())
                .orElseThrow();
        token.setAccessToken(session.getEncryptedAccessToken());
        token.setRefreshToken(session.getEncryptedRefreshToken());
        token.setExpiresAt(OffsetDateTime.ofInstant(session.getTokenExpiresAt(), ZoneId.systemDefault()));
        oAuthTokenRepository.save(token);

        onboardingSessionStore.invalidate(sessionToken);
    }
}
