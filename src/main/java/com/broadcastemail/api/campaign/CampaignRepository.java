package com.broadcastemail.api.campaign;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {
    Page<Campaign> findByAccountId(UUID accountId, Pageable pageable);
    Optional<Campaign> findByAccountIdAndId(UUID accountId, UUID campaignId);
    List<Campaign> findByAccountId(UUID accountId);

    @Modifying
    @Query("DELETE FROM Campaign c WHERE c.sentAt < :cutoff AND c.accountId IN (SELECT a.id FROM Account a WHERE a.plan = :plan)")
    void deleteByPlanAndSentAtBefore(@Param("plan") String plan, @Param("cutoff") OffsetDateTime cutoff);
}