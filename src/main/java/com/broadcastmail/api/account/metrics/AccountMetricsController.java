package com.broadcastmail.api.account.metrics;


import com.broadcastmail.api.account.metrics.dto.AccountMetricsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/account/metrics")
@RequiredArgsConstructor
public class AccountMetricsController {

    private final AccountMetricsService accountMetricsService;
    @GetMapping
    public ResponseEntity<AccountMetricsResponse> getMetrics(
            @AuthenticationPrincipal UUID accountId) {
        return ResponseEntity.ok(accountMetricsService.getMetrics(accountId));
    }
}
