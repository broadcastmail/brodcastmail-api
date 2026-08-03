package com.broadcastmail.api.campaign.confirm;

import com.broadcastmail.api.campaign.confirm.dto.RecipientRow;
import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.campaign.CampaignStatus;
import com.broadcastmail.common.campaign.recipient.CampaignRecipient;
import com.broadcastmail.common.campaign.recipient.CampaignRecipientRepository;
import com.broadcastmail.common.campaign.recipient.RecipientStatus;
import com.broadcastmail.common.outbox.OutboxEntry;
import com.broadcastmail.common.outbox.OutboxEntryRepository;
import com.broadcastmail.common.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CampaignPersistenceService {
    private final CampaignRecipientRepository campaignRecipientRepository;
    private final OutboxEntryRepository outboxEntryRepository;
    private final CampaignRepository campaignRepository;



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
