package com.broadcastmail.api.connection.schema;

import com.broadcastmail.api.connection.dto.DetectedColumn;
import com.broadcastmail.api.connection.dto.FilterableColumnResponse;
import com.broadcastmail.api.connection.dto.SchemaIntrospectionResult;
import com.broadcastmail.api.supabase.management.ManagementSqlRunners;
import com.broadcastmail.common.campaign.filter.FilterSource;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import com.broadcastmail.common.filterablecolumn.FilterableColumn;
import com.broadcastmail.common.filterablecolumn.FilterableColumnRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchemaServiceTest {

    @Mock
    private SchemaIntrospectionService schemaIntrospectionService;

    @Mock
    private ConnectionRepository connectionRepository;

    @Mock
    private FilterableColumnRepository filterableColumnRepository;

    @Mock
    private ManagementSqlRunners managementSqlRunners;

    @InjectMocks
    private SchemaService schemaService;

    @Test
    void detectForAccountShouldReflectPersistedEnabledState() {
        UUID accountId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        Connection connection = new Connection();
        connection.setId(connectionId);
        connection.setProjectRef("project-ref");
        connection.setEncryptedCreds("creds");
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.of(connection));
        when(schemaIntrospectionService.introspect(any())).thenReturn(
                new SchemaIntrospectionResult.Detected("profiles", "public", "id",
                        List.of(new DetectedColumn("plan", "text", true, 3, false),
                                new DetectedColumn("country", "text", true, 3, false)),
                        List.of()));
        when(filterableColumnRepository.findByConnectionId(connectionId)).thenReturn(List.of(
                FilterableColumn.builder()
                        .connectionId(connectionId).columnName("plan").columnType("text").displayName("plan")
                        .source(FilterSource.PROFILE_TABLE)
                        .enabled(true).cardinalityWarning(false).build()));

        SchemaIntrospectionResult.Detected result =
                (SchemaIntrospectionResult.Detected) schemaService.detectForAccount(accountId, null);

       assertThat(result.filterableColumns())
                .extracting(c -> c.columnName() + ":" + c.enabled())
                .containsExactly("plan:true", "country:false");
    }

    @Test
    void editableColumnsShouldOfferUnsavedAuthColumnsForAuthOnlyConnection() {
        // Given
        UUID accountId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        Connection connection = new Connection();
        connection.setId(connectionId);
        connection.setProjectRef("project-ref");
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.of(connection));
        when(schemaIntrospectionService.introspectSaved(any(), any())).thenReturn(
                new SchemaIntrospectionResult.Detected(null, null, null, List.of(),
                        List.of(new DetectedColumn("created_at", "datetime", false, 10, false),
                                new DetectedColumn("last_sign_in_at", "datetime", false, 10, false))));
        when(filterableColumnRepository.findByConnectionId(connectionId)).thenReturn(List.of(
                FilterableColumn.builder()
                        .connectionId(connectionId).columnName("created_at").columnType("datetime").displayName("created_at")
                        .source(FilterSource.AUTH_METADATA)
                        .enabled(true).cardinalityWarning(false).build()));

        // When
        List<FilterableColumnResponse> result = schemaService.editableColumns(accountId);

        // Then
        assertThat(result)
                .extracting(c -> c.source() + ":" + c.columnName() + ":" + c.enabled())
                .containsExactly("AUTH_METADATA:created_at:true", "AUTH_METADATA:last_sign_in_at:false");
    }
}
