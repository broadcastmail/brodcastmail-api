package com.broadcastmail.api.connection.schema;

import com.broadcastmail.api.common.exceptions.ConnectionNotFoundException;
import com.broadcastmail.api.connection.dto.FilterableColumnResponse;
import com.broadcastmail.api.connection.dto.SchemaIntrospectionResult;
import com.broadcastmail.api.filterablecolumn.FilterableColumnFactory;
import com.broadcastmail.api.supabase.management.ManagementSqlRunner;
import com.broadcastmail.api.supabase.management.ManagementSqlRunners;
import com.broadcastmail.common.campaign.filter.FilterSource;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import com.broadcastmail.common.filterablecolumn.FilterableColumn;
import com.broadcastmail.common.filterablecolumn.FilterableColumnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Stream;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Schema detection for an existing connection (settings page). Onboarding's equivalent is OnboardingSchemaService. */
@Service
@RequiredArgsConstructor
public class SchemaService {

    private final SchemaIntrospectionService schemaIntrospectionService;
    private final ConnectionRepository connectionRepository;
    private final FilterableColumnRepository filterableColumnRepository;
    private final ManagementSqlRunners managementSqlRunners;

    public SchemaIntrospectionResult detectForAccount(UUID accountId, String projectRef) {
        Connection connection = connectionRepository.findByAccountId(accountId).orElseThrow(ConnectionNotFoundException::new);

        ManagementSqlRunner sql = managementSqlRunners.forAccount(
                accountId, projectRef != null ? projectRef : connection.getProjectRef());

        SchemaIntrospectionResult result = schemaIntrospectionService.introspect(sql);

        if (projectRef != null && !projectRef.equals(connection.getProjectRef())) {
            return result;
        }
        List<FilterableColumn> persisted = filterableColumnRepository.findByConnectionId(connection.getId());
        UnaryOperator<SchemaIntrospectionResult.Detected> applyPersisted =
                d -> new SchemaIntrospectionResult.Detected(
                        d.userTableName(), d.userTableSchema(), d.userIdColumn(),
                        FilterableColumnFactory.applyPersisted(d.filterableColumns(), persisted, FilterSource.PROFILE_TABLE),
                        FilterableColumnFactory.applyPersisted(d.authColumns(), persisted, FilterSource.AUTH_METADATA));

        return switch (result) {
            case SchemaIntrospectionResult.Detected d -> applyPersisted.apply(d);
            case SchemaIntrospectionResult.MultipleCandidates(List<SchemaIntrospectionResult.Detected> candidates) ->
                    new SchemaIntrospectionResult.MultipleCandidates(candidates.stream().map(applyPersisted).toList());
            case SchemaIntrospectionResult.NotDetected n -> n;
        };
    }

    public List<FilterableColumnResponse> editableColumns(UUID accountId) {
        Connection connection = connectionRepository.findByAccountId(accountId).orElseThrow(ConnectionNotFoundException::new);
        SchemaIntrospectionResult.Detected detected = schemaIntrospectionService.introspectSaved(
                managementSqlRunners.forAccount(accountId, connection.getProjectRef()), connection);
        List<FilterableColumn> persisted = filterableColumnRepository.findByConnectionId(connection.getId());
        return Stream.concat(
                FilterableColumnFactory.applyPersisted(detected.authColumns(), persisted, FilterSource.AUTH_METADATA).stream()
                        .map(c -> FilterableColumnResponse.from(c, FilterSource.AUTH_METADATA)),
                FilterableColumnFactory.applyPersisted(detected.filterableColumns(), persisted, FilterSource.PROFILE_TABLE).stream()
                        .map(c -> FilterableColumnResponse.from(c, FilterSource.PROFILE_TABLE))
        ).toList();
    }
}
