package com.broadcastmail.api.connection;

import com.broadcastmail.api.filterablecolumn.FilterableColumn;
import com.broadcastmail.api.filterablecolumn.FilterableColumnFactory;
import com.broadcastmail.api.filterablecolumn.FilterableColumnRepository;
import com.broadcastmail.api.onboarding.OnboardingSession;
import com.broadcastmail.common.campaign.filter.FilterSource;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ConnectionService {

    private final ConnectionRepository connectionRepository;
    private final FilterableColumnRepository filterableColumnRepository;

    public void createConnection(UUID accountId, OnboardingSession session) {
        Connection connection = Connection.builder()
                .accountId(accountId)
                .projectRef(session.getProjectRef())
                .projectUrl(session.getProjectUrl())
                .encryptedCreds(session.getEncryptedRolePassword())
                .userTableSchema(session.getSchemaDetails().userSchema())
                .userTableName(session.getSchemaDetails().userTable())
                .emailColumn("email")
                .userIdColumn(session.getSchemaDetails().userIdColumn())
                .build();
        connectionRepository.save(connection);

        if (session.getConfirmedColumnNames() != null) {
            List<FilterableColumn> filterableColumns = new ArrayList<>();
            filterableColumns.addAll(FilterableColumnFactory.from(
                    connection.getId(), session.getDetectedColumns(), FilterSource.PROFILE_TABLE, session.getConfirmedColumnNames()));
            filterableColumns.addAll(FilterableColumnFactory.from(
                    connection.getId(), session.getAuthColumns(), FilterSource.AUTH_METADATA, session.getConfirmedColumnNames()));
            filterableColumnRepository.saveAll(filterableColumns);
        }
    }
}
