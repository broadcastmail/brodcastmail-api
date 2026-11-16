package com.broadcastmail.api.audience;

import com.broadcastmail.api.audience.dto.AudienceListResponse;
import com.broadcastmail.api.campaign.filter.CampaignFilterValidator;
import com.broadcastmail.api.campaign.filter.dto.FilterRequest;
import com.broadcastmail.api.common.exceptions.ConnectionNotFoundException;
import com.broadcastmail.common.filterablecolumn.FilterableColumn;
import com.broadcastmail.common.filterablecolumn.FilterableColumnRepository;
import com.broadcastmail.api.supabase.TenantFilterSqlBuilder;
import com.broadcastmail.api.supabase.ConnectionQueryContext;
import com.broadcastmail.api.supabase.SupabaseSql;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.campaign.filter.FilterSource;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import com.broadcastmail.common.suppressions.Suppression;
import com.broadcastmail.common.suppressions.SuppressionsRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.SortDirection;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AudienceService {

    private final ConnectionRepository connectionRepository;
    private final FilterableColumnRepository filterableColumnRepository;
    private final TenantAudienceGateway tenantAudienceGateway;
    private final CampaignFilterValidator filterValidator;
    private final SuppressionsRepository suppressionsRepository;

    public AudienceListResponse list(
            Account account,
            Optional<String> cursor,
            int limit,
            List<FilterRequest> filters,
            String filterRootLogic,
            String sortKey,
            SortDirection sortDirection) {

        if (!SupabaseSql.IDENTIFIER_PATTERN.matcher(sortKey).matches()) {
            throw new IllegalArgumentException("Invalid sort key: " + sortKey);
        }
        filterValidator.validateRequests(account, filters);

        Connection connection = connectionRepository.findByAccountId(account.getId())
                .orElseThrow(ConnectionNotFoundException::new);

        FilterSource sortSource = filterableColumnRepository
                .findByConnectionIdAndColumnName(connection.getId(), sortKey)
                .map(FilterableColumn::getSource)
                .orElse(FilterSource.AUTH_METADATA);
        ConnectionQueryContext ctx = ConnectionQueryContext.from(connection);
        SupabaseSql.SortSpec sort = ctx.buildSortSpec(sortSource, sortKey, sortDirection);

        TenantFilterSqlBuilder.FilterClause where = TenantFilterSqlBuilder.buildWhereClause(filters, filterRootLogic);
        String baseWhere = where.sql();
        List<Object> filterParams = new ArrayList<>(where.params());
        AudienceCursor.PageQuery page = AudienceCursor.buildPageQuery(cursor, baseWhere, filterParams, sort, ctx.idRef());

        String sql = ctx.buildAudienceQuery(baseWhere, page.whereClause(), sort);

        try {
            TenantAudienceGateway.AudienceQueryResult result = tenantAudienceGateway.queryAudience(connection, sql, filterParams, page.pageParams(), limit);
            String nextCursor = result.recipients().size() == limit
                    ? AudienceCursor.encode(result.recipients().getLast(), sortKey)
                    : null;
            List<AudienceListResponse.AudienceRecipient> marked = markSuppressed(account.getId(), result.recipients());
            return new AudienceListResponse(marked, nextCursor, nextCursor != null, result.total());
        } catch (SQLException e) {
            throw new RuntimeException("Audience list failed: " + e.getMessage(), e);
        }
    }

    private List<AudienceListResponse.AudienceRecipient> markSuppressed(
            UUID accountId, List<AudienceListResponse.AudienceRecipient> recipients) {
        if (recipients.isEmpty()) return recipients;
        List<String> emails = recipients.stream().map(AudienceListResponse.AudienceRecipient::email).toList();
        List<String> suppressed = suppressionsRepository.findByAccountIdAndEmailIn(accountId, emails).stream().map(Suppression::getEmail).toList();
        if (suppressed.isEmpty()) return recipients;
        return recipients.stream()
                .map(r -> new AudienceListResponse.AudienceRecipient(
                        r.id(), r.email(), r.attributes(), suppressed.contains(r.email())))
                .toList();
    }

}
