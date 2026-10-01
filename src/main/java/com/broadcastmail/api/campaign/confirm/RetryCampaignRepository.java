package com.broadcastmail.api.campaign.confirm;

import com.broadcastmail.common.campaign.Campaign;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Set-based copies used to build a retry campaign from the failed recipients of
 * an existing one, so no recipient rows are loaded into memory.
 */
public interface RetryCampaignRepository extends Repository<Campaign, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO campaign_recipients
                (id, campaign_id, external_user_id, email, status, idempotency_key, created_at)
            SELECT gen_random_uuid(),
                   :retryCampaignId,
                   external_user_id,
                   email,
                   'queued',
                   CAST(:retryCampaignId AS text) || ':' || external_user_id,
                   now()
            FROM campaign_recipients
            WHERE campaign_id = :originalCampaignId
              AND status = 'failed'
            """, nativeQuery = true)
    int copyFailedRecipients(@Param("originalCampaignId") UUID originalCampaignId,
                             @Param("retryCampaignId") UUID retryCampaignId);

    @Modifying
    @Query(value = """
            INSERT INTO outbox (id, campaign_recipient_id, status, attempts, next_attempt_at)
            SELECT gen_random_uuid(), id, 'pending', 0, now()
            FROM campaign_recipients
            WHERE campaign_id = :retryCampaignId
            """, nativeQuery = true)
    int enqueueRecipients(@Param("retryCampaignId") UUID retryCampaignId);
}
