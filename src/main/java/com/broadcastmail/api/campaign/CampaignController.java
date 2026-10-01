package com.broadcastmail.api.campaign;


import com.broadcastmail.api.campaign.confirm.CampaignConfirmService;
import com.broadcastmail.api.campaign.dto.*;
import com.broadcastmail.api.common.exceptions.CampaignNotFoundException;
import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.campaign.CampaignStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static reactor.netty.http.HttpConnectionLiveness.log;

@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
public class CampaignController {


    private final CampaignService campaignService;
    private final RecipientPreviewService recipientPreviewService;
    private final CampaignConfirmService campaignConfirmService;
    private final TaskExecutor sseTaskExecutor;
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
        return ResponseEntity.status(HttpStatus.CREATED).body(CampaignResponse.from(campaignService.createCampaign(accountId, request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CampaignResponse> getCampaign(@AuthenticationPrincipal UUID accountId, @PathVariable UUID id) {
        return ResponseEntity.ok(CampaignResponse.from(campaignService.getCampaign(accountId, id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CampaignResponse> updateCampaign(@AuthenticationPrincipal UUID accountId, @PathVariable UUID id,
                                                           @RequestBody @Valid UpdateCampaignRequest request) {
        return ResponseEntity.ok(CampaignResponse.from(campaignService.updateCampaign(accountId, id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCampaign(@AuthenticationPrincipal UUID accountId, @PathVariable UUID id) {
        campaignService.deleteCampaign(accountId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<RecipientPreviewResponse> previewRecipients(@AuthenticationPrincipal UUID accountId, @PathVariable UUID id) {
        int count = recipientPreviewService.preview(accountId, id);
        return ResponseEntity.ok(new RecipientPreviewResponse(count));
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

    @GetMapping("/{id}/status/stream")
    public SseEmitter streamStatus(@AuthenticationPrincipal UUID accountId, @PathVariable UUID id) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 minutes timeout
        sseTaskExecutor.execute(() -> {
            try {
                while (true) {
                    Campaign campaign = campaignRepository.findByAccountIdAndId(accountId,id).orElseThrow(() -> new CampaignNotFoundException(id));
                    emitter.send(SseEmitter.event().name("status").data(CampaignStatusEvent.from(campaign)));
                    if (isTerminal(campaign.getStatus())) {
                        emitter.complete();
                        return;
                    }
                    Thread.sleep(2000); // Poll every second
                }
            } catch (IOException _) {
                log.debug("SSE connection closed for campaign {}", id);
            } catch (InterruptedException _) {
                // thread interrupted — exit cleanly
                Thread.currentThread().interrupt();
                emitter.complete();
            } catch (Exception e) {
                log.error("SSE error for campaign {}", id, e);
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @GetMapping("/status/stream")
    public SseEmitter streamAllStatus(
            @AuthenticationPrincipal UUID accountId) {

        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onTimeout(emitter::complete);

        sseTaskExecutor.execute(() -> {
            try {
                while (true) {
                    List<Campaign> activeCampaigns = campaignRepository
                            .findByAccountIdAndStatusIn(accountId,
                                    List.of(CampaignStatus.RESOLVING, CampaignStatus.SENDING));

                    if (activeCampaigns.isEmpty()) {
                        emitter.complete();
                        return;
                    }

                    for (Campaign campaign : activeCampaigns) {
                        emitter.send(SseEmitter.event()
                                .name("status")
                                .data(CampaignStatusEvent.from(campaign)));
                    }

                    Thread.sleep(2000);
                }
            } catch (IOException _) {
                log.debug("Dashboard SSE connection closed for account {}", accountId);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                emitter.complete();
            } catch (Exception e) {
                log.error("Dashboard SSE error for account {}", accountId, e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private boolean isTerminal(CampaignStatus status) {
        return status == CampaignStatus.SENT || status == CampaignStatus.FAILED || status == CampaignStatus.PARTIALLY_FAILED;
    }
}
