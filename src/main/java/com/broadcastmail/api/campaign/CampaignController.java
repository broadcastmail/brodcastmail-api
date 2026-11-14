package com.broadcastmail.api.campaign;


import com.broadcastmail.api.account.plan.PlanRequired;
import com.broadcastmail.api.campaign.confirm.CampaignConfirmService;
import com.broadcastmail.api.campaign.dto.*;
import com.broadcastmail.api.campaign.history.projection.RecipientHistoryResponse;
import com.broadcastmail.api.campaign.history.RecipientHistoryService;
import com.broadcastmail.api.campaign.sse.CampaignSseRegistry;
import com.broadcastmail.common.account.plan.PlanFeature;
import com.broadcastmail.api.common.exceptions.CampaignNotFoundException;
import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.campaign.CampaignStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
public class CampaignController {


    private final CampaignService campaignService;
    private final RecipientPreviewService recipientPreviewService;
    private final CampaignConfirmService campaignConfirmService;
    private final RecipientHistoryService recipientHistoryService;
    private final CampaignSseRegistry sseRegistry;
    private final CampaignRepository campaignRepository;


    @GetMapping
    public ResponseEntity<Page<CampaignSummaryResponse>> listCampaigns(@AuthenticationPrincipal UUID accountId,
                                                                       @PageableDefault(size = 20, sort = "createdAt", direction =
                                                                               Sort.Direction.DESC)
                                                                       Pageable pageable) {
        return ResponseEntity.ok(campaignService.listCampaigns(accountId, pageable).map(CampaignSummaryResponse::from));
    }

    @PostMapping
    public ResponseEntity<CampaignResponse> createCampaign(@AuthenticationPrincipal UUID accountId,
                                                           @RequestBody @Valid CreateCampaignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(withFilters(campaignService.createCampaign(accountId, request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CampaignResponse> getCampaign(@AuthenticationPrincipal UUID accountId, @PathVariable UUID id) {
        return ResponseEntity.ok(withFilters(campaignService.getCampaign(accountId, id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CampaignResponse> updateCampaign(@AuthenticationPrincipal UUID accountId, @PathVariable UUID id,
                                                           @RequestBody @Valid UpdateCampaignRequest request) {
        return ResponseEntity.ok(withFilters(campaignService.updateCampaign(accountId, id, request)));
    }

    private CampaignResponse withFilters(Campaign campaign) {
        return CampaignResponse.from(campaign, campaignService.getFilters(campaign.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCampaign(@AuthenticationPrincipal UUID accountId, @PathVariable UUID id) {
        campaignService.deleteCampaign(accountId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/recipients")
    public ResponseEntity<Page<RecipientResponse>> getRecipients(
            @AuthenticationPrincipal UUID accountId,
            @PathVariable UUID id,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(campaignService.getCampaignRecipients(accountId, id, status, pageable));
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<RecipientPreviewResponse> previewRecipients(@AuthenticationPrincipal UUID accountId, @PathVariable UUID id) {
        return ResponseEntity.ok(recipientPreviewService.preview(accountId, id));
    }

    @PostMapping("/{id}/test/send")
    public ResponseEntity<Void> sendTestEmail(@AuthenticationPrincipal UUID accountId, @PathVariable UUID id,
                                              @RequestBody @Valid TestEmailRequest request) {
        campaignService.sendTestEmail(accountId, id, request);
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/{id}/schedule")
    public ResponseEntity<Void> unscheduleCampaign(@AuthenticationPrincipal UUID accountId, @PathVariable UUID id) {
        campaignService.unscheduleCampaign(accountId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/acknowledge-rate-limit")
    public ResponseEntity<Void> acknowledgeRateLimit(@AuthenticationPrincipal UUID accountId, @PathVariable UUID id) {
        campaignService.acknowledgeRateLimit(accountId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<Void> confirmCampaign(@AuthenticationPrincipal UUID accountId, @PathVariable UUID id) {
        campaignConfirmService.confirmCampaign(accountId, id);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<CampaignResponse> retryCampaign(@AuthenticationPrincipal UUID accountId, @PathVariable UUID id) {
        return campaignConfirmService.retryFailed(accountId, id)
                .map(retry -> ResponseEntity.accepted().body(CampaignResponse.from(retry)))
                .orElseGet(() -> ResponseEntity.<CampaignResponse>accepted().build());
    }

    @PostMapping("/{id}/recipients/retry-failed")
    public ResponseEntity<CampaignResponse> retryFailed(@AuthenticationPrincipal UUID accountId, @PathVariable UUID id) {
        Campaign retry = campaignConfirmService.retryPartiallyFailed(accountId, id);
        return ResponseEntity.accepted().body(CampaignResponse.from(retry));
    }

    @GetMapping("/recipient-history")
    @PlanRequired(PlanFeature.EXCLUSIONS)
    public ResponseEntity<Page<RecipientHistoryResponse>> listRecipientHistory(
            @AuthenticationPrincipal UUID accountId,
            @RequestParam(required = false) UUID excludeCampaignId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(recipientHistoryService.listHistory(accountId, excludeCampaignId, pageable));
    }

    @GetMapping("/{id}/status/stream")
    public SseEmitter streamStatus(@AuthenticationPrincipal UUID accountId, @PathVariable UUID id) {
        Campaign campaign = campaignRepository.findByAccountIdAndId(accountId, id)
                .orElseThrow(() -> new CampaignNotFoundException(id));
        SseEmitter emitter = new SseEmitter(300_000L);
        try {
            emitter.send(SseEmitter.event().name("status").data(CampaignStatusEvent.from(campaign)));
        } catch (Exception e) {
            emitter.completeWithError(e);
            return emitter;
        }
        if (isTerminal(campaign.getStatus())) {
            emitter.complete();
            return emitter;
        }
        sseRegistry.registerCampaign(id, emitter);
        return emitter;
    }

    @GetMapping("/status/stream")
    public SseEmitter streamAllStatus(@AuthenticationPrincipal UUID accountId) {
        SseEmitter emitter = new SseEmitter(300_000L);
        List<Campaign> active = campaignRepository.findByAccountIdAndStatusIn(
                accountId, List.of(CampaignStatus.RESOLVING, CampaignStatus.SENDING));
        try {
            for (Campaign c : active) {
                emitter.send(SseEmitter.event().name("status").data(CampaignStatusEvent.from(c)));
            }
        } catch (Exception e) {
            emitter.completeWithError(e);
            return emitter;
        }
        if (active.isEmpty()) {
            emitter.complete();
            return emitter;
        }
        sseRegistry.registerAccount(accountId, emitter);
        return emitter;
    }

    private boolean isTerminal(CampaignStatus status) {
        return status == CampaignStatus.SENT || status == CampaignStatus.FAILED || status == CampaignStatus.PARTIALLY_FAILED;
    }
}
