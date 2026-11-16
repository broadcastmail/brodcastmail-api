package com.broadcastmail.api.campaign.history;

import com.broadcastmail.api.campaign.history.projection.RecipientHistoryResponse;
import com.broadcastmail.common.campaign.CampaignStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecipientHistoryService {

    private static final List<CampaignStatus> SENT_STATUSES =
            List.of(CampaignStatus.SENT, CampaignStatus.PARTIALLY_FAILED, CampaignStatus.FAILED);

    private final RecipientHistoryRepository recipientHistoryRepository;

    public Page<RecipientHistoryResponse> listHistory(
            UUID accountId,
            UUID currentCampaignId,
            Pageable pageable) {
        return recipientHistoryRepository.findHistory(accountId, SENT_STATUSES, currentCampaignId, pageable);
    }
}
