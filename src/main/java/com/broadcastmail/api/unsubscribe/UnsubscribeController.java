package com.broadcastmail.api.unsubscribe;

import com.broadcastmail.api.config.AppProperties;
import com.broadcastmail.common.campaign.recipient.CampaignRecipientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/unsubscribe")
@RequiredArgsConstructor
public class UnsubscribeController {
    private final UnsubscribeTokenVerifier unsubscribeTokenVerifier;
    private final CampaignRecipientRepository campaignRecipientRepository;
    private final AppProperties appProperties;

    @GetMapping
    public ResponseEntity<Void> unsubscribe(
            @RequestParam String token
    ) {
        UUID recipientId = unsubscribeTokenVerifier.verify(token);
        campaignRecipientRepository.markUnsubscribed(recipientId);
        return ResponseEntity
                .status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, appProperties.frontend().url() + "/unsubscribed")
                .build();
    }
}
