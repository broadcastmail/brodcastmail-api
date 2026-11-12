package com.broadcastmail.api.campaign;

import com.broadcastmail.api.audience.TenantAudienceGateway;
import com.broadcastmail.api.campaign.filter.CampaignFilterValidator;
import com.broadcastmail.api.common.exceptions.CampaignNotFoundException;
import com.broadcastmail.api.supabase.ConnectionQueryContext;
import com.broadcastmail.api.supabase.SupabaseSql;
import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignStatus;
import com.broadcastmail.common.campaign.filter.CampaignFilter;
import com.broadcastmail.common.campaign.filter.CampaignFilterRepository;
import com.broadcastmail.common.campaign.filter.CampaignFilterSerializer;
import com.broadcastmail.common.campaign.filter.FilterOperator;
import com.broadcastmail.common.campaign.filter.FilterSource;
import com.broadcastmail.common.campaign.recipient.CampaignRecipientRepository;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import com.broadcastmail.common.suppressions.Suppression;
import com.broadcastmail.common.suppressions.SuppressionsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;



import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipientPreviewServiceTest {

    @Mock
    private CampaignService campaignService;

    @Mock
    private ConnectionRepository connectionRepository;

    @Mock
    private CampaignFilterRepository filterRepository;

    @Mock
    private CampaignFilterValidator filterValidator;

    @Mock
    private TenantAudienceGateway tenantAudienceGateway;
    @Mock
    private SuppressionsRepository suppressionsRepository;
    @Mock
    private CampaignRecipientRepository recipientRepository;

    private RecipientPreviewService recipientPreviewService;

    @BeforeEach
    void setUp() {
        recipientPreviewService = new RecipientPreviewService(campaignService, connectionRepository, tenantAudienceGateway, filterRepository,
                new CampaignFilterSerializer(), filterValidator, suppressionsRepository, recipientRepository);
    }

    private Connection buildConnection(UUID accountId) {
        return Connection.builder().accountId(accountId).projectRef("project-ref").userTableSchema("public").userTableName("profiles")
                .userIdColumn("id").build();
    }

    private Campaign stubEmptyCampaign(UUID accountId, UUID campaignId) {
        Campaign campaign = Campaign.builder().id(campaignId).accountId(accountId).status(CampaignStatus.DRAFT).build();
        when(campaignService.getCampaign(accountId, campaignId)).thenReturn(campaign);
        return campaign;
    }

    @Test
    void shouldRejectPreviewOfCampaignNotOwnedByAccount() {
        UUID accountId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        when(campaignService.getCampaign(accountId, campaignId)).thenThrow(new CampaignNotFoundException(campaignId));

        assertThatThrownBy(() -> recipientPreviewService.preview(accountId, campaignId)).isInstanceOf(CampaignNotFoundException.class);
        verifyNoInteractions(filterRepository, connectionRepository);
    }

    @Test
    void shouldRejectPreviewWhenSavedFiltersAreStale() {
        UUID accountId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        stubEmptyCampaign(accountId, campaignId);
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.of(buildConnection(accountId)));
        List<CampaignFilter> filters = List.of(CampaignFilter.builder().columnName("gone").build());
        when(filterRepository.findByCampaignId(campaignId)).thenReturn(filters);
        doThrow(new com.broadcastmail.api.common.exceptions.InvalidCampaignFilterException(List.of("gone"))).when(filterValidator)
                .validateSaved(accountId, filters);

        assertThatThrownBy(() -> recipientPreviewService.preview(accountId, campaignId)).isInstanceOf(
                com.broadcastmail.api.common.exceptions.InvalidCampaignFilterException.class);
        verify(connectionRepository, never()).save(any());
    }

    @Test
    void shouldReturnCountAndPersistEstimatedUserCount() throws SQLException {
        UUID accountId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        stubEmptyCampaign(accountId, campaignId);
        Connection connection = buildConnection(accountId);
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.of(connection));
        when(filterRepository.findByCampaignId(campaignId)).thenReturn(List.of());
        when(tenantAudienceGateway.countRecipients(any(), anyString(), any(), any())).thenReturn(42);

        Integer count = recipientPreviewService.preview(accountId, campaignId);

        assertThat(count).isEqualTo(42);
        verify(connectionRepository).save(connection);
    }

    @Test
    void shouldThrowCampaignNotFoundWhenNoConnectionExists() {
        UUID accountId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        stubEmptyCampaign(accountId, campaignId);
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recipientPreviewService.preview(accountId, campaignId)).isInstanceOf(CampaignNotFoundException.class);
    }

    @Test
    void shouldWrapSqlExceptionAsRuntimeException() throws SQLException {
        UUID accountId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        stubEmptyCampaign(accountId, campaignId);
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.of(buildConnection(accountId)));
        when(filterRepository.findByCampaignId(campaignId)).thenReturn(List.of());
        when(tenantAudienceGateway.countRecipients(any(), anyString(), any(), any())).thenThrow(new SQLException("connection refused"));

        assertThatThrownBy(() -> recipientPreviewService.preview(accountId, campaignId)).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("connection refused");
    }

    @Test
    void shouldApplyCampaignFiltersToGeneratedQuery() throws SQLException {
        UUID accountId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        stubEmptyCampaign(accountId, campaignId);
        Connection connection = buildConnection(accountId);
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.of(connection));
        when(filterRepository.findByCampaignId(campaignId)).thenReturn(
                List.of(CampaignFilter.builder().columnName("plan").operator(FilterOperator.EQ).filterValue("pro").filterOrder(0)
                        .source(FilterSource.PROFILE_TABLE).build()));
        String expectedSql = ConnectionQueryContext.from(connection).buildRecipientCountQuery() + " WHERE \"plan\" = ?";
        when(tenantAudienceGateway.countRecipients(any(), anyString(), any(), any())).thenReturn(7);

        recipientPreviewService.preview(accountId, campaignId);

        verify(tenantAudienceGateway).countRecipients(any(), eq(expectedSql), any(), any());
    }

    @Test
    void shouldNotJoinProfileTableWhenThereAreNoFilters() throws SQLException {
        UUID accountId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        stubEmptyCampaign(accountId, campaignId);
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.of(buildConnection(accountId)));
        when(filterRepository.findByCampaignId(campaignId)).thenReturn(List.of());
        when(tenantAudienceGateway.countRecipients(any(), anyString(), any(), any())).thenReturn(100);

        recipientPreviewService.preview(accountId, campaignId);

        verify(tenantAudienceGateway).countRecipients(any(), eq(SupabaseSql.COUNT_RECIPIENTS), any(), any());
    }

    @Test
    void shouldApplySuppressionEmailsToExclusionSet() throws SQLException {
        UUID accountId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        stubEmptyCampaign(accountId, campaignId);
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.of(buildConnection(accountId)));
        when(filterRepository.findByCampaignId(campaignId)).thenReturn(List.of());
        Suppression suppression = mock(Suppression.class);
        when(suppression.getEmail()).thenReturn("suppressed@example.com");
        when(suppressionsRepository.findByAccountId(accountId)).thenReturn(List.of(suppression));
        when(tenantAudienceGateway.countRecipients(any(), anyString(), any(), any())).thenReturn(10);

        recipientPreviewService.preview(accountId, campaignId);

        verify(tenantAudienceGateway).countRecipients(any(), anyString(), any(), eq(Set.of("suppressed@example.com")));
    }

    @Test
    void shouldApplyExcludedIdsAsEmailsToExclusionSet() throws SQLException {
        UUID accountId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        Campaign campaign =
                Campaign.builder().id(campaignId).accountId(accountId).status(CampaignStatus.DRAFT).excludedIds(List.of("external-user-1")).build();
        when(campaignService.getCampaign(accountId, campaignId)).thenReturn(campaign);
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.of(buildConnection(accountId)));
        when(filterRepository.findByCampaignId(campaignId)).thenReturn(List.of());
        when(suppressionsRepository.findByAccountId(accountId)).thenReturn(List.of());
        when(recipientRepository.findDistinctEmailsByExternalUserIds(accountId, List.of("external-user-1"))).thenReturn(
                List.of("excluded@example.com"));
        when(tenantAudienceGateway.countRecipients(any(), anyString(), any(), any())).thenReturn(5);

        recipientPreviewService.preview(accountId, campaignId);

        verify(recipientRepository).findDistinctEmailsByExternalUserIds(accountId, List.of("external-user-1"));
        verify(tenantAudienceGateway).countRecipients(any(), anyString(), any(), eq(Set.of("excluded@example.com")));
    }

    @Test
    void shouldApplyExcludedCampaignIdsToExclusionSet() throws SQLException {
        UUID accountId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID prevCampaignId = UUID.randomUUID();
        Campaign campaign =
                Campaign.builder().id(campaignId).accountId(accountId).status(CampaignStatus.DRAFT).excludedCampaignId(prevCampaignId).build();
        when(campaignService.getCampaign(accountId, campaignId)).thenReturn(campaign);
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.of(buildConnection(accountId)));
        when(filterRepository.findByCampaignId(campaignId)).thenReturn(List.of());
        when(suppressionsRepository.findByAccountId(accountId)).thenReturn(List.of());
        when(recipientRepository.findDistinctEmailsByCampaignId(prevCampaignId)).thenReturn(List.of("past-recipient@example.com"));
        when(tenantAudienceGateway.countRecipients(any(), anyString(), any(), any())).thenReturn(8);

        recipientPreviewService.preview(accountId, campaignId);

        verify(recipientRepository).findDistinctEmailsByCampaignId(prevCampaignId);
        verify(tenantAudienceGateway).countRecipients(any(), anyString(), any(), eq(Set.of("past-recipient@example.com")));
    }

    @Test
    void shouldDeduplicateEmailsAcrossAllExclusionSources() throws SQLException {
        UUID accountId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID prevCampaignId = UUID.randomUUID();
        Campaign campaign =
                Campaign.builder().id(campaignId).accountId(accountId).status(CampaignStatus.DRAFT).excludedIds(List.of("external-user-1"))
                        .excludedCampaignId(prevCampaignId).build();
        when(campaignService.getCampaign(accountId, campaignId)).thenReturn(campaign);
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.of(buildConnection(accountId)));
        when(filterRepository.findByCampaignId(campaignId)).thenReturn(List.of());
        Suppression suppression = mock(Suppression.class);
        when(suppression.getEmail()).thenReturn("shared@example.com");
        when(suppressionsRepository.findByAccountId(accountId)).thenReturn(List.of(suppression));
        when(recipientRepository.findDistinctEmailsByExternalUserIds(accountId, List.of("external-user-1"))).thenReturn(
                List.of("shared@example.com"));
        when(recipientRepository.findDistinctEmailsByCampaignId(prevCampaignId)).thenReturn(List.of("shared@example.com", "other@example.com"));
        when(tenantAudienceGateway.countRecipients(any(), anyString(), any(), any())).thenReturn(3);

        recipientPreviewService.preview(accountId, campaignId);

        verify(tenantAudienceGateway).countRecipients(any(), anyString(), any(), eq(Set.of("shared@example.com", "other@example.com")));
    }
}
