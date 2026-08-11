package com.broadcastmail.api.campaign;


import com.broadcastmail.api.campaign.confirm.CampaignConfirmService;
import com.broadcastmail.api.campaign.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/campaigns")
public class CampaignController {


    private final CampaignService campaignService;
    private final RecipientPreviewService recipientPreviewService;
    private final CampaignConfirmService campaignConfirmService;

    public CampaignController(CampaignService campaignService, RecipientPreviewService recipientPreviewService, CampaignConfirmService campaignConfirmService) {
        this.campaignService = campaignService;
        this.recipientPreviewService = recipientPreviewService;
        this.campaignConfirmService = campaignConfirmService;
    }

    @GetMapping
    public ResponseEntity<Page<CampaignSummaryResponse>> listCampaigns(
            @AuthenticationPrincipal UUID accountId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                campaignService.listCampaigns(accountId, pageable)
                        .map(CampaignSummaryResponse::from)
        );
    }

    @PostMapping
    public ResponseEntity<CampaignResponse> createCampaign(
            @AuthenticationPrincipal UUID accountId,
            @RequestBody @Valid CreateCampaignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CampaignResponse.from(
                        campaignService.createCampaign(accountId, request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CampaignResponse> getCampaign(
            @AuthenticationPrincipal UUID accountId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(
                CampaignResponse.from(
                        campaignService.getCampaign(accountId, id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CampaignResponse> updateCampaign(
            @AuthenticationPrincipal UUID accountId,
            @PathVariable UUID id,
            @RequestBody UpdateCampaignRequest request) {
        return ResponseEntity.ok(
                CampaignResponse.from(
                        campaignService.updateCampaign(accountId, id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCampaign(
            @AuthenticationPrincipal UUID accountId,
            @PathVariable UUID id) {
        campaignService.deleteCampaign(accountId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<RecipientPreviewResponse> previewRecipients(
            @AuthenticationPrincipal UUID accountId,
            @PathVariable UUID id) {
        int count = recipientPreviewService.preview(accountId, id);
        return ResponseEntity.ok(new RecipientPreviewResponse(count));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<Void> confirmCampaign(
            @AuthenticationPrincipal UUID accountId,
            @PathVariable UUID id) {
        campaignConfirmService.confirmCampaign(accountId, id);
        return ResponseEntity.accepted().build();
    }
}
