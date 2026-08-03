package com.broadcastmail.api.campaign.filter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CampaignFilterRepository extends JpaRepository<CampaignFilter, UUID> {
    List<CampaignFilter> findByCampaignId(UUID campaignId);
}