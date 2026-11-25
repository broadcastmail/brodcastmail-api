package com.broadcastmail.api.campaign;

import com.broadcastmail.api.common.exceptions.CampaignInProgressException;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.campaign.CampaignStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Refuses changes the worker depends on while one of the account's campaigns is resolving or sending: the project,
 * reader role, table or email column it reads recipients from, the Resend key and sender it sends with, and the
 * account itself. A rate-limited campaign stays {@code SENDING} until it finishes, so it blocks too.
 */
@Component
@RequiredArgsConstructor
public class CampaignInProgressGuard {

    private static final List<CampaignStatus> IN_PROGRESS = List.of(CampaignStatus.RESOLVING, CampaignStatus.SENDING);

    private final CampaignRepository campaignRepository;

    public void requireNoneInProgress(UUID accountId) {
        campaignRepository.findByAccountIdAndStatusIn(accountId, IN_PROGRESS).stream()
                .findFirst()
                .ifPresent(campaign -> {
                    throw new CampaignInProgressException(campaign.getId(), campaign.getName());
                });
    }
}
