package com.broadcastemail.api.campaign.confirm;

import com.broadcastemail.api.campaign.Campaign;
import com.broadcastemail.api.campaign.CampaignRepository;
import com.broadcastemail.api.campaign.CampaignStatus;
import com.broadcastemail.api.campaign.confirm.dto.RecipientRow;
import com.broadcastemail.api.campaign.recipent.CampaignRecipient;
import com.broadcastemail.api.campaign.recipent.CampaignRecipientRepository;
import com.broadcastemail.api.campaign.recipent.RecipientStatus;
import com.broadcastemail.api.outbox.OutboxEntry;
import com.broadcastemail.api.outbox.OutboxEntryRepository;
import com.broadcastemail.api.outbox.OutboxStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class CampaignPersistenceService {
    private final CampaignRecipientRepository campaignRecipientRepository;
    private final OutboxEntryRepository outboxEntryRepository;
    private final CampaignRepository campaignRepository;

    public CampaignPersistenceService(CampaignRecipientRepository campaignRecipientRepository, OutboxEntryRepository outboxEntryRepository, CampaignRepository campaignRepository) {
        this.campaignRecipientRepository = campaignRecipientRepository;
        this.outboxEntryRepository = outboxEntryRepository;
        this.campaignRepository = campaignRepository;
    }

    @Transactional
    public void persistConfirmation(UUID campaignId, List<RecipientRow> resolved, Campaign campaign) {
        List<CampaignRecipient> recipients = resolved.stream()
                .map(r -> CampaignRecipient.builder()
                        .campaignId(campaignId)
                        .externalUserId(r.userId())
                        .email(r.email())
                        .status(RecipientStatus.QUEUED)
                        .idempotencyKey(campaignId + ":" + r.userId())
                        .build())
                .toList();
        campaignRecipientRepository.saveAll(recipients);

        List<OutboxEntry> outboxEntries = recipients.stream()
                .map(r -> OutboxEntry.builder()
                        .campaignRecipientId(r.getId())
                        .status(OutboxStatus.PENDING)
                        .attempts(0)
                        .nextAttemptAt(OffsetDateTime.now(ZoneId.systemDefault()))
                        .build())
                .toList();
        outboxEntryRepository.saveAll(outboxEntries);

        campaign.setStatus(CampaignStatus.SENDING);
        campaign.setRecipientCount(resolved.size());
        campaignRepository.save(campaign);
    }
}
