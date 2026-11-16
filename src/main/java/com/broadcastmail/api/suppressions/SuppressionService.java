package com.broadcastmail.api.suppressions;

import com.broadcastmail.common.campaign.recipient.CampaignRecipientRepository;
import com.broadcastmail.common.suppressions.SuppressionsRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SuppressionService {

    private final CampaignRecipientRepository campaignRecipientRepository;
    private final SuppressionsRepository suppressionsRepository;

    @Transactional
    public void unsubscribe(UUID recipientId) {
        campaignRecipientRepository.markUnsubscribed(recipientId);
        suppressionsRepository.insertFromRecipientWithReason(recipientId, "UNSUBSCRIBE");
    }
}
