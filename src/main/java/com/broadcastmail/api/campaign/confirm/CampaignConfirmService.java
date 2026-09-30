package com.broadcastmail.api.campaign.confirm;

import com.broadcastmail.api.account.plan.PlanFacade;
import com.broadcastmail.api.campaign.CampaignService;
import com.broadcastmail.api.common.exceptions.AccountNotFoundException;
import com.broadcastmail.api.common.exceptions.CampaignNotEditableException;
import com.broadcastmail.api.common.exceptions.CampaignNotFoundException;
import com.broadcastmail.api.common.exceptions.CampaignNotRetryableException;
import com.broadcastmail.api.common.exceptions.ConnectionNotFoundException;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import com.broadcastmail.common.account.plan.PlanFeature;
import com.broadcastmail.common.campaign.filter.CampaignFilterRepository;
import com.broadcastmail.common.connection.ConnectionRepository;
import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignRetryRepository;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.campaign.CampaignStatus;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CampaignConfirmService {

    private final CampaignService campaignService;
    private final CampaignRepository campaignRepository;
    private final ConnectionRepository connectionRepository;
    private final CampaignRetryRepository retryCampaignRepository;
    private final AccountRepository accountRepository;
    private final CampaignFilterRepository campaignFilterRepository;
    private final PlanFacade planFacade;

    @Transactional
    public void confirmCampaign(UUID accountId, UUID campaignId) {
        Campaign campaign = campaignService.getCampaign(accountId, campaignId);
        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new CampaignNotEditableException();
        }

        connectionRepository.findByAccountId(accountId)
                .orElseThrow(ConnectionNotFoundException::new);

        Account account = accountRepository.findById(accountId)
                .orElseThrow(AccountNotFoundException::new);

        if (!campaignFilterRepository.findByCampaignId(campaignId).isEmpty()) {
            planFacade.enforceFeature(account, PlanFeature.FILTERS);
        }

        campaign.setStatus(CampaignStatus.RESOLVING);
        campaignRepository.save(campaign);
    }


    /**
     * Retries a FAILED campaign. If resolution failed (no failed recipients exist) the same
     * campaign is sent back to resolution and empty is returned; if every recipient failed,
     * a retry campaign is created for them instead.
     */
    @Transactional
    public Optional<Campaign> retryFailed(UUID accountId, UUID campaignId) {
        Campaign campaign = getCampaignInStatus(accountId, campaignId, CampaignStatus.FAILED);
        long failedRecipients = countFailedRecipients(campaignId);
        if (failedRecipients == 0) {
            restartResolution(campaign);
            return Optional.empty();
        }
        return Optional.of(createRetryCampaign(campaign, failedRecipients));
    }

    /**
     * Creates a new campaign that resends only to the FAILED recipients of a
     * PARTIALLY_FAILED one. The original campaign and its recipients stay untouched.
     */
    @Transactional
    public Campaign retryPartiallyFailed(UUID accountId, UUID campaignId) {
        Campaign campaign = getCampaignInStatus(accountId, campaignId, CampaignStatus.PARTIALLY_FAILED);
        long failedRecipients = countFailedRecipients(campaignId);
        if (failedRecipients == 0) {
            throw new CampaignNotRetryableException();
        }
        return createRetryCampaign(campaign, failedRecipients);
    }

    private Campaign getCampaignInStatus(UUID accountId, UUID campaignId, CampaignStatus expectedStatus) {
        Campaign campaign = retryCampaignRepository.findByAccountIdAndIdForUpdate(accountId, campaignId)
                .orElseThrow(() -> new CampaignNotFoundException(campaignId));
        if (campaign.getStatus() != expectedStatus) {
            throw new CampaignNotRetryableException();
        }
        return campaign;
    }

    private long countFailedRecipients(UUID campaignId) {
        return retryCampaignRepository.countFailedRecipients(campaignId);
    }

    private void restartResolution(Campaign campaign) {
        campaign.setStatus(CampaignStatus.RESOLVING);
        campaign.setSentCount(0);
        campaign.setDeliveredCount(0);
        campaign.setOpenedCount(0);
        campaign.setBouncedCount(0);
        campaign.setFailedCount(0);
        campaignRepository.save(campaign);
    }

    private Campaign createRetryCampaign(Campaign original, long failedRecipients) {
        if (retryCampaignRepository.hasRetryCampaign(original.getId())) {
            throw new CampaignNotRetryableException();
        }
        Campaign retry = campaignRepository.saveAndFlush(Campaign.builder()
                .accountId(original.getAccountId())
                .connectionId(original.getConnectionId())
                .retryOfCampaignId(original.getId())
                .name(original.getName() + " (retry)")
                .subject(original.getSubject())
                .bodyHtml(original.getBodyHtml())
                .status(CampaignStatus.SENDING)
                .recipientCount(Math.toIntExact(failedRecipients))
                .build());

        int copied = retryCampaignRepository.copyFailedRecipients(original.getId(), retry.getId());
        if (copied != failedRecipients) {
            throw new IllegalStateException("Failed recipient count changed while creating retry campaign");
        }
        int enqueued = retryCampaignRepository.enqueueRecipients(retry.getId());
        if (enqueued != copied) {
            throw new IllegalStateException("Could not enqueue every retry recipient");
        }
        return retry;
    }
}