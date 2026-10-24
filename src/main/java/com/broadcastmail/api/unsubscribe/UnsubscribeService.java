package com.broadcastmail.api.unsubscribe;

import com.broadcastmail.common.campaign.recipient.CampaignRecipientRepository;
import com.broadcastmail.common.unsubscribe.UnsubscribeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UnsubscribeService {

    private final CampaignRecipientRepository campaignRecipientRepository;
    private final UnsubscribeRepository unsubscribeRepository;

    @Transactional
    public void unsubscribe(UUID recipientId) {
        campaignRecipientRepository.markUnsubscribed(recipientId);
        unsubscribeRepository.insertFromRecipient(recipientId);
    }
}
