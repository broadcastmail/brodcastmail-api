package com.broadcastmail.api.audience;

import com.broadcastmail.api.audience.dto.AudienceListResponse;
import com.broadcastmail.api.campaign.filter.dto.FilterRequest;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.SortDirection;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audience")
@RequiredArgsConstructor
public class AudienceController {

    private final AudienceService audienceService;

    @PostMapping("/list")
    public ResponseEntity<AudienceListResponse> listAudience(
            @AuthenticationPrincipal UUID accountId,
            @RequestParam(required = false) Optional<String> cursor,
            @RequestParam(defaultValue = "50") @Max(100) int limit,
            @RequestParam(defaultValue = "created_at") String sortKey,
            @RequestParam(defaultValue = "DESCENDING") SortDirection sortDirection,
            @RequestBody List<FilterRequest> filters) {

        return ResponseEntity.ok(audienceService.list(accountId, cursor, limit, filters, sortKey, sortDirection));
    }
}
