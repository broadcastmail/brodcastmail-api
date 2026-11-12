package com.broadcastmail.api.campaign.history;

import com.broadcastmail.api.campaign.history.projection.RecipientHistoryResponse;
import com.broadcastmail.common.campaign.CampaignStatus;
import com.broadcastmail.common.campaign.recipient.CampaignRecipient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.UUID;

public interface RecipientHistoryRepository extends Repository<CampaignRecipient, UUID> {

    @Query(
        value = """
            SELECT new com.broadcastmail.api.campaign.history.projection.RecipientHistoryResponse(
                c.id, c.name, c.sentAt, r.email, r.externalUserId, r.status)
            FROM CampaignRecipient r, Campaign c
            WHERE r.campaignId = c.id
              AND c.accountId = :accountId
              AND c.status IN :statuses
              AND (:currentCampaignId IS NULL OR c.id != :currentCampaignId)
            ORDER BY c.sentAt DESC NULLS LAST, r.email ASC
            """,
        countQuery = """
            SELECT COUNT(r)
            FROM CampaignRecipient r, Campaign c
            WHERE r.campaignId = c.id
              AND c.accountId = :accountId
              AND c.status IN :statuses
              AND (:currentCampaignId IS NULL OR c.id != :currentCampaignId)
            """
    )
    Page<RecipientHistoryResponse> findHistory(
            @Param("accountId") UUID accountId,
            @Param("statuses") Collection<CampaignStatus> statuses,
            @Param("currentCampaignId") UUID currentCampaignId,
            Pageable pageable);
}
