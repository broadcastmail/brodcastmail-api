package com.broadcastmail.api.audience;

import com.broadcastmail.api.audience.dto.AudienceListResponse;
import com.broadcastmail.api.campaign.filter.CampaignFilterValidator;
import com.broadcastmail.api.campaign.filter.dto.FilterRequest;
import com.broadcastmail.api.common.exceptions.ConnectionNotFoundException;
import com.broadcastmail.api.filterablecolumn.FilterableColumn;
import com.broadcastmail.api.filterablecolumn.FilterableColumnRepository;
import com.broadcastmail.api.supabase.CustomerConnectionFactory;
import com.broadcastmail.api.supabase.SupabaseSql;
import com.broadcastmail.common.campaign.filter.FilterSource;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.SortDirection;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AudienceService {

    private final ConnectionRepository connectionRepository;
    private final FilterableColumnRepository filterableColumnRepository;
    private final CustomerConnectionFactory connectionFactory;
    private final CampaignFilterValidator filterValidator;

    public AudienceListResponse list(
            UUID accountId,
            Optional<String> cursor,
            int limit,
            List<FilterRequest> filters,
            String sortKey,
            SortDirection sortDirection) {

        if (!SupabaseSql.IDENTIFIER_PATTERN.matcher(sortKey).matches()) {
            throw new IllegalArgumentException("Invalid sort key: " + sortKey);
        }
        if (!filters.isEmpty()) {
            filterValidator.validateRequests(accountId, filters);
        }

        Connection connection = connectionRepository.findByAccountId(accountId)
                .orElseThrow(ConnectionNotFoundException::new);

        FilterSource sortSource = filterableColumnRepository
                .findByConnectionIdAndColumnName(connection.getId(), sortKey)
                .map(FilterableColumn::getSource)
                .orElse(FilterSource.AUTH_METADATA);
        String sortAlias = sortSource == FilterSource.PROFILE_TABLE ? "linked" : "auth";
        SupabaseSql.SortSpec sort = new SupabaseSql.SortSpec(sortAlias, sortKey, sortDirection);

        String baseWhere = SupabaseSql.buildWhereClause(filters);
        List<Object> countParams = SupabaseSql.buildFilterParams(filters);
        AudienceCursor.PageQuery page = AudienceCursor.buildPageQuery(cursor, baseWhere, countParams, sort);

        String sql = SupabaseSql.buildAudienceQuery(
                hasLinkedTable(connection), connection.getUserTableSchema(), connection.getUserTableName(),
                connection.getUserIdColumn(), baseWhere, page.whereClause(), sort);

        try (java.sql.Connection conn = connectionFactory.open(connection)) {
            SupabaseSql.AudienceQueryResult result = SupabaseSql.queryAudience(conn, sql, countParams, page.pageParams(), limit);
            String nextCursor = result.recipients().size() == limit
                    ? AudienceCursor.encode(result.recipients().getLast(), sortKey)
                    : null;
            return new AudienceListResponse(result.recipients(), nextCursor, nextCursor != null, result.total());
        } catch (SQLException e) {
            throw new RuntimeException("Audience list failed: " + e.getMessage(), e);
        }
    }

    private static boolean hasLinkedTable(Connection connection) {
        return connection.getUserTableName() != null;
    }
}
