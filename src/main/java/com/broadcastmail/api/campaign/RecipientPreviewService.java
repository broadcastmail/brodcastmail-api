package com.broadcastmail.api.campaign;

import com.broadcastmail.api.campaign.filter.CampaignFilterValidator;
import com.broadcastmail.common.campaign.filter.CampaignFilter;
import com.broadcastmail.common.campaign.filter.CampaignFilterRepository;
import com.broadcastmail.api.common.exceptions.CampaignNotFoundException;
import com.broadcastmail.common.campaign.filter.CampaignFilterSerializer;
import com.broadcastmail.common.campaign.filter.FilterQuery;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import com.broadcastmail.api.supabase.CustomerConnectionFactory;
import com.broadcastmail.api.supabase.ConnectionQueryContext;
import com.broadcastmail.common.suppressions.Suppression;
import com.broadcastmail.common.suppressions.SuppressionsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecipientPreviewService {

    private final CampaignService campaignService;
    private final ConnectionRepository connectionRepository;
    private final CustomerConnectionFactory connectionFactory;
    private final CampaignFilterRepository filterRepository;
    private final CampaignFilterSerializer filterSerializer;
    private final CampaignFilterValidator filterValidator;
    private final SuppressionsRepository suppressionsRepository;

    public Integer preview(UUID accountId, UUID campaignId) {
        campaignService.getCampaign(accountId, campaignId);
        Connection connection = connectionRepository.findByAccountId(accountId)
                .orElseThrow(() -> new CampaignNotFoundException(campaignId));

        List<CampaignFilter> filters = filterRepository.findByCampaignId(campaignId);
        filterValidator.validateSaved(accountId, filters);
        FilterQuery filterQuery = filterSerializer.serialize(filters);

        List<String> suppressed = suppressionsRepository.findByAccountId(accountId).stream()
                .map(Suppression::getEmail)
                .toList();
        boolean hasFilters = !filterQuery.sql().isEmpty();
        boolean hasSuppressed = !suppressed.isEmpty();

        String sql = buildCountSql(connection, filterQuery, hasFilters, hasSuppressed);

        try (java.sql.Connection conn = connectionFactory.open(connection);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            int paramIndex = 1;
            for (Object param : filterQuery.parameters()) {
                stmt.setObject(paramIndex++, param);
            }
            if (hasSuppressed) {
                Array suppressedArray = conn.createArrayOf("text", suppressed.toArray());
                stmt.setArray(paramIndex, suppressedArray);
            }

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int count = rs.getInt(1);
                connection.setEstimatedUserCount(count);
                connectionRepository.save(connection);
                return count;
            }
            return 0;

        } catch (SQLException e) {
            throw new RuntimeException("Recipient preview failed: " + e.getMessage(), e);
        }
    }

    private String buildCountSql(Connection connection, FilterQuery filterQuery,
                                  boolean hasFilters, boolean hasSuppressed) {
        ConnectionQueryContext ctx = ConnectionQueryContext.from(connection);
        if (!hasFilters) {
            String base = ctx.buildBaseCountQuery();
            return hasSuppressed ? base + " WHERE " + ctx.emailColumnExpression() + " != ALL(?)" : base;
        }
        String base = ctx.buildRecipientCountQuery() + " " + filterQuery.sql();
        return hasSuppressed ? base + " AND " + ctx.emailColumnExpression() + " != ALL(?)" : base;
    }
}
