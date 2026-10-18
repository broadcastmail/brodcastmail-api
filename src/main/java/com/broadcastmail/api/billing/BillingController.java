package com.broadcastmail.api.billing;

import com.broadcastmail.api.billing.dto.SubscriptionResponse;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import com.broadcastmail.common.account.plan.Plan;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@Tag(name = "billing")
@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {

    private static final String ACCOUNT_NOT_FOUND = "Account not found";

    private final BillingService billingService;
    private final AccountRepository accountRepository;

    @PostMapping("/checkout")
    public ResponseEntity<Map<String, String>> createCheckoutSession(
            @AuthenticationPrincipal UUID accountId) throws StripeException {

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ACCOUNT_NOT_FOUND));

        if (account.getPlan() == Plan.PRO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Already on Pro plan");
        }

        String url = billingService.createCheckoutSession(accountId);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping("/subscription")
    public ResponseEntity<SubscriptionResponse> getSubscription(
            @AuthenticationPrincipal UUID accountId) {

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ACCOUNT_NOT_FOUND));

        try {
            return ResponseEntity.ok(billingService.getSubscription(account));
        } catch (StripeException _) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch subscription from Stripe");
        }
    }

    @PostMapping("/portal")
    public ResponseEntity<Map<String, String>> createPortalSession(
            @AuthenticationPrincipal UUID accountId) throws StripeException {

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ACCOUNT_NOT_FOUND));

        if (account.getStripeCustomerId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No Stripe customer found — upgrade first");
        }

        String url = billingService.createPortalSession(account.getStripeCustomerId());
        return ResponseEntity.ok(Map.of("url", url));
    }
}
