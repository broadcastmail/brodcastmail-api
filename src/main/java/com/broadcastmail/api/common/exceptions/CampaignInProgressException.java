package com.broadcastmail.api.common.exceptions;

import lombok.Getter;

import java.util.UUID;

/** A change to the connection, the email provider or the account was refused because a campaign is resolving or sending. */
@Getter
public class CampaignInProgressException extends RuntimeException {
    private final UUID campaignId;
    private final String campaignName;

    public CampaignInProgressException(UUID campaignId, String campaignName) {
        super("CAMPAIGN_IN_PROGRESS");
        this.campaignId = campaignId;
        this.campaignName = campaignName;
    }
}
