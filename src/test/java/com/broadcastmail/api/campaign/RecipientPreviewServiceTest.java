package com.broadcastmail.api.campaign;

import com.broadcastmail.api.audience.TenantAudienceGateway;
import com.broadcastmail.api.campaign.dto.RecipientPreviewResponse;
import com.broadcastmail.api.campaign.filter.CampaignFilterValidator;
import com.broadcastmail.api.common.exceptions.CampaignNotFoundException;
import com.broadcastmail.api.campaign.dto.PreviewSelectionRequest;
import com.broadcastmail.api.campaign.filter.dto.FilterRequest;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import com.broadcastmail.common.account.plan.Plan;
import com.broadcastmail.common.campaign.AudienceMode;
import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.campaign.CampaignStatus;
import com.broadcastmail.common.campaign.filter.CampaignFilter;
import com.broadcastmail.common.campaign.filter.CampaignFilterRepository;
import com.broadcastmail.common.campaign.filter.CampaignFilterSerializer;
import com.broadcastmail.common.campaign.filter.FilterOperator;
import com.broadcastmail.common.campaign.filter.FilterSource;
import com.broadcastmail.common.campaign.recipient.CampaignRecipientRepository;
import com.broadcastmail.common.usage.RecipientUsageRepository;
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
import static org.mockito.AdditionalMatchers.and;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipientPreviewServiceTest {

    @Mock
    private CampaignRepository campaignRepository;

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
    @Mock
    private RecipientUsageRepository recipientUsageRepository;
    @Mock
    private AccountRepository accountRepository;

    private RecipientPreviewService recipientPreviewService;

    @BeforeEach
    void setUp() {
        recipientPreviewService = new RecipientPreviewService(campaignRepository, connectionRepository, tenantAudienceGateway, filterRepository,
                new CampaignFilterSerializer(), filterValidator, suppressionsRepository, recipientRepository, recipientUsageRepository, accountRepository);
    }

    private void givenAccount() {
        Account account = mock(Account.class);
        when(account.getPlan()).thenReturn(Plan.FREE);
        when(accountRepository.findById(any())).thenReturn(Optional.of(account));
    }

    private Connection buildConnection(UUID accountId) {
        return Connection.builder().accountId(accountId).projectRef("project-ref").userTableSchema("public").userTableName("profiles")
                .userIdColumn("id").build();
    }

    private Campaign stubEmptyCampaign(UUID accountId, UUID campaignId) {
        Campaign campaign = Campaign.builder().id(campaignId).accountId(accountId).status(CampaignStatus.DRAFT).build();
        when(campaignRepository.findByAccountIdAndId(accountId, campaignId)).thenReturn(Optional.of(campaign));
        return campaign;
    }

    @Test
    void shouldRejectPreviewOfCampaignNotOwnedByAccount() {
        UUID accountId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.findByAccountIdAndId(accountId, campaignId)).thenReturn(Optional.empty());

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
        when(tenantAudienceGateway.count(any(), anyString(), any())).thenReturn(new TenantAudienceGateway.Counts(42, 42));
        givenAccount();

        RecipientPreviewResponse response = recipientPreviewService.preview(accountId, campaignId);

        assertThat(response.recipientCount()).isEqualTo(42);
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
        when(tenantAudienceGateway.count(any(), anyString(), any())).thenThrow(new SQLException("connection refused"));

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
        when(tenantAudienceGateway.count(any(), anyString(), any())).thenReturn(new TenantAudienceGateway.Counts(7, 7));
        givenAccount();

        recipientPreviewService.preview(accountId, campaignId);

        verify(tenantAudienceGateway).count(any(), and(contains(" JOIN \"public\".\"profiles\" linked"),
                contains(" AND (linked.\"plan\" = ?)")), eq(List.of("pro")));
    }

    @Test
    void shouldNotJoinProfileTableWhenThereAreNoFilters() throws SQLException {
        UUID accountId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        stubEmptyCampaign(accountId, campaignId);
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.of(buildConnection(accountId)));
        when(filterRepository.findByCampaignId(campaignId)).thenReturn(List.of());
        when(tenantAudienceGateway.count(any(), anyString(), any())).thenReturn(new TenantAudienceGateway.Counts(100, 100));
        givenAccount();

        recipientPreviewService.preview(accountId, campaignId);

        verify(tenantAudienceGateway).count(any(),
                eq("SELECT COUNT(*), COUNT(*) FROM _broadcastmail.user_emails auth WHERE auth.\"email\" IS NOT NULL"), eq(List.of()));
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
        when(tenantAudienceGateway.count(any(), anyString(), any())).thenReturn(new TenantAudienceGateway.Counts(10, 10));
        givenAccount();

        recipientPreviewService.preview(accountId, campaignId);

        verify(tenantAudienceGateway).count(any(), contains("auth.\"email\" <> ALL(?)"),
                argThat(params -> hasArray(params, "suppressed@example.com")));
    }

    @Test
    void shouldExcludeUntickedIdsEvenWhenTheyWereNeverEmailed() throws SQLException {
        UUID accountId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        Campaign campaign =
                Campaign.builder().id(campaignId).accountId(accountId).status(CampaignStatus.DRAFT).excludedIds(List.of("external-user-1")).build();
        when(campaignRepository.findByAccountIdAndId(accountId, campaignId)).thenReturn(Optional.of(campaign));
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.of(buildConnection(accountId)));
        when(filterRepository.findByCampaignId(campaignId)).thenReturn(List.of());
        when(suppressionsRepository.findByAccountId(accountId)).thenReturn(List.of());
        when(tenantAudienceGateway.count(any(), anyString(), any())).thenReturn(new TenantAudienceGateway.Counts(5, 5));
        givenAccount();

        recipientPreviewService.preview(accountId, campaignId);

        verify(tenantAudienceGateway).count(any(), contains("auth.\"id\"::text <> ALL(?)"),
                argThat(params -> hasArray(params, "external-user-1")));
    }

    @Test
    void shouldApplyExcludedCampaignIdsToExclusionSet() throws SQLException {
        UUID accountId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID prevCampaignId = UUID.randomUUID();
        Campaign campaign =
                Campaign.builder().id(campaignId).accountId(accountId).status(CampaignStatus.DRAFT).excludedCampaignId(prevCampaignId).build();
        when(campaignRepository.findByAccountIdAndId(accountId, campaignId)).thenReturn(Optional.of(campaign));
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.of(buildConnection(accountId)));
        when(filterRepository.findByCampaignId(campaignId)).thenReturn(List.of());
        when(suppressionsRepository.findByAccountId(accountId)).thenReturn(List.of());
        when(recipientRepository.findDistinctEmailsByCampaignId(prevCampaignId)).thenReturn(List.of("past-recipient@example.com"));
        when(tenantAudienceGateway.count(any(), anyString(), any())).thenReturn(new TenantAudienceGateway.Counts(8, 8));
        givenAccount();

        recipientPreviewService.preview(accountId, campaignId);

        verify(recipientRepository).findDistinctEmailsByCampaignId(prevCampaignId);
        verify(tenantAudienceGateway).count(any(), anyString(), argThat(params -> hasArray(params, "past-recipient@example.com")));
    }

    @Test
    void shouldDeduplicateEmailsAcrossAllExclusionSources() throws SQLException {
        UUID accountId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID prevCampaignId = UUID.randomUUID();
        Campaign campaign =
                Campaign.builder().id(campaignId).accountId(accountId).status(CampaignStatus.DRAFT).excludedCampaignId(prevCampaignId).build();
        when(campaignRepository.findByAccountIdAndId(accountId, campaignId)).thenReturn(Optional.of(campaign));
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.of(buildConnection(accountId)));
        when(filterRepository.findByCampaignId(campaignId)).thenReturn(List.of());
        Suppression suppression = mock(Suppression.class);
        when(suppression.getEmail()).thenReturn("shared@example.com");
        when(suppressionsRepository.findByAccountId(accountId)).thenReturn(List.of(suppression));
        when(recipientRepository.findDistinctEmailsByCampaignId(prevCampaignId)).thenReturn(List.of("shared@example.com", "other@example.com"));
        when(tenantAudienceGateway.count(any(), anyString(), any())).thenReturn(new TenantAudienceGateway.Counts(3, 3));
        givenAccount();

        recipientPreviewService.preview(accountId, campaignId);

        verify(tenantAudienceGateway).count(any(), anyString(),
                argThat(params -> hasArray(params, "shared@example.com", "other@example.com")));
    }

    @Test
    void shouldCountOnlyPickedRecipientsIgnoringFiltersWhenSelectedManually() throws SQLException {
        UUID accountId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        Campaign campaign = Campaign.builder().id(campaignId).accountId(accountId).status(CampaignStatus.DRAFT)
                .audienceMode(AudienceMode.MANUAL).includedIds(List.of("u1", "u2")).build();
        when(campaignRepository.findByAccountIdAndId(accountId, campaignId)).thenReturn(Optional.of(campaign));
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.of(buildConnection(accountId)));
        when(filterRepository.findByCampaignId(campaignId)).thenReturn(
                List.of(CampaignFilter.builder().columnName("plan").operator(FilterOperator.EQ).filterValue("pro").filterOrder(0)
                        .source(FilterSource.PROFILE_TABLE).build()));
        when(tenantAudienceGateway.count(any(), anyString(), any())).thenReturn(new TenantAudienceGateway.Counts(2, 2));
        givenAccount();

        RecipientPreviewResponse response = recipientPreviewService.preview(accountId, campaignId);

        assertThat(response.recipientCount()).isEqualTo(2);
        verify(tenantAudienceGateway).count(any(), and(contains("auth.\"id\"::text = ANY(?)"), not(contains("plan"))),
                argThat(params -> hasArray(params, "u1", "u2")));
    }

    @Test
    void shouldReturnZeroWithoutQueryingWhenNobodyIsPicked() throws SQLException {
        UUID accountId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        Campaign campaign = Campaign.builder().id(campaignId).accountId(accountId).status(CampaignStatus.DRAFT)
                .audienceMode(AudienceMode.MANUAL).includedIds(List.of()).build();
        when(campaignRepository.findByAccountIdAndId(accountId, campaignId)).thenReturn(Optional.of(campaign));
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.of(buildConnection(accountId)));
        when(filterRepository.findByCampaignId(campaignId)).thenReturn(List.of());
        givenAccount();

        assertThat(recipientPreviewService.preview(accountId, campaignId).recipientCount()).isZero();
        verify(tenantAudienceGateway, never()).count(any(), anyString(), any());
    }

    @Test
    void shouldCountUnsavedSelectionWithoutPersistingTheEstimate() throws SQLException {
        UUID accountId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        stubEmptyCampaign(accountId, campaignId);
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.of(buildConnection(accountId)));
        when(tenantAudienceGateway.count(any(), anyString(), any())).thenReturn(new TenantAudienceGateway.Counts(9, 9));
        givenAccount();
        PreviewSelectionRequest request = new PreviewSelectionRequest(
                List.of(new FilterRequest("created_at", FilterOperator.AFTER, "2026-01-01", FilterSource.AUTH_METADATA,
                        null, null, null, null),
                        new FilterRequest("plan", FilterOperator.EQ, "pro", FilterSource.PROFILE_TABLE, null, null, null, null)),
                "OR", "all", List.of(), List.of("u3"), null);

        RecipientPreviewResponse response = recipientPreviewService.previewSelection(accountId, campaignId, request);

        assertThat(response.recipientCount()).isEqualTo(9);
        verify(tenantAudienceGateway).count(any(),
                contains("(auth.\"created_at\" > ? OR linked.\"plan\" = ?) AND auth.\"id\"::text <> ALL(?)"),
                argThat(params -> params.size() == 3 && hasArray(params, "u3")));
        verify(connectionRepository, never()).save(any());
    }

    @Test
    void shouldRejectExcludingAnotherAccountsCampaign() {
        UUID accountId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID foreignCampaignId = UUID.randomUUID();
        stubEmptyCampaign(accountId, campaignId);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(mock(Account.class)));
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.of(buildConnection(accountId)));
        when(campaignRepository.findByAccountIdAndId(accountId, foreignCampaignId)).thenReturn(Optional.empty());
        PreviewSelectionRequest request = new PreviewSelectionRequest(List.of(), "AND", "all", List.of(), List.of(), foreignCampaignId);

        assertThatThrownBy(() -> recipientPreviewService.previewSelection(accountId, campaignId, request))
                .isInstanceOf(CampaignNotFoundException.class);
        verifyNoInteractions(tenantAudienceGateway, recipientRepository);
    }

    @Test
    void shouldReportHowManyRecipientsWereNotEmailedThisPeriod() throws SQLException {
        UUID accountId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        stubEmptyCampaign(accountId, campaignId);
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.of(buildConnection(accountId)));
        when(filterRepository.findByCampaignId(campaignId)).thenReturn(List.of());
        when(recipientUsageRepository.findEmailsSince(eq(accountId), any())).thenReturn(List.of("old@example.com"));
        when(tenantAudienceGateway.count(any(), anyString(), any())).thenReturn(new TenantAudienceGateway.Counts(10, 9));
        givenAccount();

        RecipientPreviewResponse response = recipientPreviewService.preview(accountId, campaignId);

        assertThat(response).isEqualTo(new RecipientPreviewResponse(10, 9, 1, Plan.FREE.strategy().recipientLimit()));
        verify(tenantAudienceGateway).count(any(),
                startsWith("SELECT COUNT(*), COUNT(*) FILTER (WHERE auth.\"email\" <> ALL(?)) FROM"),
                argThat(params -> params.getFirst() instanceof String[] used && List.of(used).equals(List.of("old@example.com"))));
    }

    private static boolean hasArray(List<Object> params, String... values) {
        return params.stream().anyMatch(p -> p instanceof String[] array && Set.of(array).equals(Set.of(values)));
    }
}
