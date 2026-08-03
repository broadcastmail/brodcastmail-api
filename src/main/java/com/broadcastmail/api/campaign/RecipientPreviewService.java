package com.broadcastmail.api.campaign;

import com.broadcastmail.api.campaign.filter.CampaignFilter;
import com.broadcastmail.api.campaign.filter.CampaignFilterRepository;
import com.broadcastmail.api.campaign.filter.FilterQuery;
import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.common.exceptions.CampaignNotFoundException;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.connection.Connection;
import com.broadcastmail.api.connection.ConnectionRepository;
import com.broadcastmail.api.supabase.SupabaseSql;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.DriverManager;
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
    private final EncryptionProperties encryptionProperties;
    private final CampaignFilterRepository filterRepository;
    private final CampaignFilterSerializer filterSerializer;

    public Integer preview(UUID accountId, UUID campaignId)
    {
        Connection connection = connectionRepository.findByAccountId(accountId)
                .orElseThrow(CampaignNotFoundException::new);

        String jdbcUrl = SupabaseSql.buildJdbcUrl(connection.getProjectRef());
        String rolePassword = SecurityUtil.decrypt(connection.getEncryptedCreds(), encryptionProperties.key());

        List<CampaignFilter> filters = filterRepository.findByCampaignId(campaignId);
        FilterQuery filterQuery = filterSerializer.serialize(filters);
        String sql = filterQuery.sql().isEmpty()
                ? SupabaseSql.COUNT_RECIPIENTS
                : SupabaseSql.COUNT_RECIPIENTS + " " + filterQuery.sql();

        try (java.sql.Connection conn = DriverManager.getConnection(jdbcUrl, "broadcastmail_reader", rolePassword);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < filterQuery.parameters().size(); i++) {
                stmt.setObject(i + 1, filterQuery.parameters().get(i));
            }

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
            return 0;

        } catch (SQLException e) {
            throw new RuntimeException("Recipient preview failed: " + e.getMessage(), e);
        }
    }
}
