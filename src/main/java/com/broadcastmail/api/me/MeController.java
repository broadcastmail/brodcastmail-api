package com.broadcastmail.api.me;

import com.broadcastmail.api.account.metrics.AccountMetricsService;
import com.broadcastmail.api.account.metrics.dto.AccountMetricsResponse;
import com.broadcastmail.api.common.exceptions.AccountNotFoundException;
import com.broadcastmail.api.me.dto.MeResponse;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "me")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {
    private final ConnectionRepository connectionRepository;
    private final AccountRepository accountRepository;
    private final AccountMetricsService accountMetricsService;

    @GetMapping
    public ResponseEntity<MeResponse> getMe(@AuthenticationPrincipal UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(AccountNotFoundException::new);

        Connection connection = connectionRepository.findByAccountId(accountId)
                .orElse(null);

        return ResponseEntity.ok(new MeResponse(
                account.getEmail(),
                account.getPlan().name(),
                connection != null ? connection.getProjectRef() : null
        ));
    }

    @GetMapping("/metrics")
    public ResponseEntity<AccountMetricsResponse> getMetrics(@AuthenticationPrincipal UUID accountId) {
        return ResponseEntity.ok(accountMetricsService.getMetrics(accountId));
    }
}
