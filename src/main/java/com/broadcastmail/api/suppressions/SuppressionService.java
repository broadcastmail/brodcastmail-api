package com.broadcastmail.api.suppressions;

import com.broadcastmail.common.campaign.recipient.CampaignRecipientRepository;
import com.broadcastmail.common.suppressions.SuppressionsRepository;
import com.broadcastmail.common.unsubscribe.UnsubscribeClaims;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SuppressionService {

    private final CampaignRecipientRepository campaignRecipientRepository;
    private final SuppressionsRepository suppressionsRepository;

    @Transactional
    public void unsubscribe(UnsubscribeClaims claims) {
        suppressionsRepository.insertWithReason(claims.accountId(), claims.email(), "UNSUBSCRIBE");
        if (campaignRecipientRepository.existsById(claims.recipientId())) {
            campaignRecipientRepository.markUnsubscribed(claims.recipientId());
        }
    }
}
