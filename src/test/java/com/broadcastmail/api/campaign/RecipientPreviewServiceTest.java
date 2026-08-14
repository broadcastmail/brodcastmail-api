package com.broadcastmail.api.campaign;

import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.common.exceptions.CampaignNotFoundException;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.common.campaign.filter.CampaignFilter;
import com.broadcastmail.common.campaign.filter.CampaignFilterRepository;
import com.broadcastmail.common.campaign.filter.CampaignFilterSerializer;
import com.broadcastmail.common.campaign.filter.FilterOperator;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipientPreviewServiceTest {

    private static final String ENCRYPTION_KEY = "12345678901234567890123456789012";

    @Mock
    private CampaignService campaignService;

    @Mock
    private ConnectionRepository connectionRepository;

    @Mock
    private CampaignFilterRepository filterRepository;

    private RecipientPreviewService recipientPreviewService;

    @BeforeEach
    void setUp() {
        recipientPreviewService = new RecipientPreviewService(
                campaignService,
                connectionRepository,
                new EncryptionProperties(ENCRYPTION_KEY),
                filterRepository,
                new CampaignFilterSerializer()
        );
    }

    private Connection buildConnection(UUID accountId) {
        return Connection.builder()
                .accountId(accountId)
                .projectRef("project-ref")
                .encryptedCreds(SecurityUtil.encrypt("role-password", ENCRYPTION_KEY))
                .build();
    }

    private java.sql.Connection stubJdbcConnection(PreparedStatement statement) throws SQLException {
        java.sql.Connection sqlConnection = mock(java.sql.Connection.class);
        when(sqlConnection.prepareStatement(anyString())).thenReturn(statement);
        return sqlConnection;
    }

    @Test
    void shouldReturnCountAndPersistEstimatedUserCount() throws SQLException {
        // Given
        UUID accountId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        Connection connection = buildConnection(accountId);
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.of(connection));
        when(filterRepository.findByCampaignId(campaignId)).thenReturn(List.of());

        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(42);
        java.sql.Connection sqlConnection = stubJdbcConnection(statement);

        try (MockedStatic<DriverManager> driverManager = mockStatic(DriverManager.class)) {
            driverManager.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(sqlConnection);

            // When
            Integer count = recipientPreviewService.preview(accountId, campaignId);

            // Then
            assertThat(count).isEqualTo(42);
        }

        // The service mutates and persists the same Connection it read the estimate onto
        verify(connectionRepository).save(connection);
    }

    @Test
    void shouldReturnZeroAndSkipPersistWhenNoRowsReturned() throws SQLException {
        // Given
        UUID accountId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.of(buildConnection(accountId)));
        when(filterRepository.findByCampaignId(campaignId)).thenReturn(List.of());

        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);
        java.sql.Connection sqlConnection = stubJdbcConnection(statement);

        try (MockedStatic<DriverManager> driverManager = mockStatic(DriverManager.class)) {
            driverManager.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(sqlConnection);

            // When
            Integer count = recipientPreviewService.preview(accountId, campaignId);

            // Then
            assertThat(count).isZero();
        }

        verify(connectionRepository, never()).save(any());
    }

    @Test
    void shouldThrowCampaignNotFoundWhenNoConnectionExists() {
        // Given
        UUID accountId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> recipientPreviewService.preview(accountId, campaignId))
                .isInstanceOf(CampaignNotFoundException.class);
    }

    @Test
    void shouldWrapSqlExceptionAsRuntimeException() {
        // Given
        UUID accountId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.of(buildConnection(accountId)));
        when(filterRepository.findByCampaignId(campaignId)).thenReturn(List.of());
        SQLException connectionRefused = new SQLException("connection refused");

        try (MockedStatic<DriverManager> driverManager = mockStatic(DriverManager.class)) {
            driverManager.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenThrow(connectionRefused);

            // When / Then
            assertThatThrownBy(() -> recipientPreviewService.preview(accountId, campaignId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("connection refused");
        }
    }

    @Test
    void shouldApplyCampaignFiltersToGeneratedQuery() throws SQLException {
        // Given
        UUID accountId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.of(buildConnection(accountId)));
        when(filterRepository.findByCampaignId(campaignId)).thenReturn(List.of(
                CampaignFilter.builder()
                        .columnName("plan")
                        .operator(FilterOperator.EQ)
                        .filterValue("pro")
                        .filterOrder(0)
                        .build()
        ));

        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(7);

        java.sql.Connection sqlConnection = mock(java.sql.Connection.class);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(sqlConnection.prepareStatement(sqlCaptor.capture())).thenReturn(statement);

        try (MockedStatic<DriverManager> driverManager = mockStatic(DriverManager.class)) {
            driverManager.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(sqlConnection);

            // When
            recipientPreviewService.preview(accountId, campaignId);
        }

        // Then
        assertThat(sqlCaptor.getValue()).isEqualTo("SELECT COUNT(*) FROM auth.user_emails WHERE \"plan\" = ?");
        verify(statement).setObject(1, "pro");
    }
}
