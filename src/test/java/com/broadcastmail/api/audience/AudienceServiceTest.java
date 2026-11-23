package com.broadcastmail.api.audience;

import com.broadcastmail.api.campaign.filter.CampaignFilterValidator;
import com.broadcastmail.api.support.CampaignTestFixtures;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.campaign.filter.FilterSource;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import com.broadcastmail.common.filterablecolumn.FilterableColumn;
import com.broadcastmail.common.filterablecolumn.FilterableColumnRepository;
import com.broadcastmail.common.suppressions.SuppressionsRepository;
import org.hibernate.query.SortDirection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AudienceServiceTest {

    @Mock
    private ConnectionRepository connectionRepository;
    @Mock
    private FilterableColumnRepository filterableColumnRepository;
    @Mock
    private TenantAudienceGateway tenantAudienceGateway;
    @Mock
    private CampaignFilterValidator filterValidator;
    @Mock
    private SuppressionsRepository suppressionsRepository;
    @InjectMocks
    private AudienceService audienceService;

    @Test
    void shouldSortByAuthColumnWhenProfileTableHasColumnWithSameName() throws Exception {
        // Given — auth-only connection whose profile table also has created_at
        UUID accountId = UUID.randomUUID();
        Account account = CampaignTestFixtures.account().id(accountId).build();
        Connection connection = CampaignTestFixtures.connection(accountId).id(UUID.randomUUID()).emailColumn(null).build();
        when(connectionRepository.findByAccountId(accountId)).thenReturn(Optional.of(connection));
        when(filterableColumnRepository.findByConnectionId(connection.getId())).thenReturn(List.of(
                column("created_at", FilterSource.AUTH_METADATA),
                column("created_at", FilterSource.PROFILE_TABLE)));
        when(tenantAudienceGateway.queryAudience(any(), anyString(), anyList(), anyList(), anyInt()))
                .thenReturn(new TenantAudienceGateway.AudienceQueryResult(0, List.of()));

        // When
        audienceService.list(account, Optional.empty(), 50, List.of(), null, "created_at", SortDirection.DESCENDING);

        // Then
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(tenantAudienceGateway).queryAudience(any(), sql.capture(), anyList(), anyList(), eq(50));
        assertThat(sql.getValue()).contains("ORDER BY auth.\"created_at\"");
    }

    private FilterableColumn column(String name, FilterSource source) {
        return FilterableColumn.builder().columnName(name).columnType("timestamptz").displayName(name)
                .source(source).enabled(true).cardinalityWarning(false).build();
    }
}
