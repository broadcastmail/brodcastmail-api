package com.broadcastemail.api.campaign.confirm;

import com.broadcastemail.api.campaign.*;
import com.broadcastemail.api.campaign.confirm.dto.RecipientRow;
import com.broadcastemail.api.campaign.filter.CampaignFilter;
import com.broadcastemail.api.campaign.filter.CampaignFilterRepository;
import com.broadcastemail.api.campaign.filter.FilterQuery;
import com.broadcastemail.api.common.SecurityUtil;
import com.broadcastemail.api.common.exceptions.CampaignNotEditableException;
import com.broadcastemail.api.common.exceptions.ConnectionNotFoundException;
import com.broadcastemail.api.common.exceptions.PlanLimitExceededException;
import com.broadcastemail.api.config.EncryptionProperties;
import com.broadcastemail.api.connection.Connection;
import com.broadcastemail.api.connection.ConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CampaignConfirmService {

    private final CampaignService campaignService;
    private final CampaignRepository campaignRepository;
    private final ConnectionRepository connectionRepository;
    private final CampaignFilterRepository filterRepository;
    private final CampaignFilterSerializer filterSerializer;
    private final EncryptionProperties encryptionProperties;
    private final RecipientResolutionService recipientResolutionService;
    private final CampaignPersistenceService campaignPersistenceService;

    public void confirmCampaign(UUID accountId, UUID campaignId) {
        // outside transaction — no DB connection held
        Campaign campaign = campaignService.getCampaign(accountId, campaignId);
        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new CampaignNotEditableException();
        }
        Connection connection = connectionRepository.findByAccountId(accountId)
                .orElseThrow(ConnectionNotFoundException::new);

        // TODO: CWE-316 — decrypt to char[] instead of String to reduce password exposure window
        String rolePassword = SecurityUtil.decrypt(connection.getEncryptedCreds(), encryptionProperties.key());
        List<CampaignFilter> filters = filterRepository.findByCampaignId(campaignId);
        FilterQuery filterQuery = filterSerializer.serialize(filters);

        // external JDBC — no Spring transaction open
        String jdbcUrl = "jdbc:postgresql://db." + connection.getProjectRef() + ".supabase.co:5432/postgres";
        List<RecipientRow> resolved = recipientResolutionService.resolve(jdbcUrl, rolePassword, connection, filterQuery);

        if (resolved.size() > 300) {
            throw new PlanLimitExceededException();
        }

        // atomic DB writes — transaction opens here

       campaignPersistenceService.persistConfirmation(campaignId, resolved, campaign);
    }


}
