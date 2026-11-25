package com.broadcastmail.api.suppressions;

import com.broadcastmail.api.config.AppProperties;
import com.broadcastmail.common.unsubscribe.UnsubscribeTokenCodec;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Tag(name = "campaigns")
@RestController
@RequestMapping("/unsubscribe")
@RequiredArgsConstructor
public class UnsubscribeController {
    private final UnsubscribeTokenCodec unsubscribeTokenCodec;
    private final SuppressionService suppressionService;
    private final AppProperties appProperties;

    /**
     * Never unsubscribes: mail security scanners (Safe Links, Mimecast, Proofpoint) fetch every GET link
     * in a message. Sends the person to the frontend confirm page, whose button POSTs back here.
     */
    @GetMapping
    public ResponseEntity<Void> confirmPage(@RequestParam String token) {
        return ResponseEntity
                .status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, appProperties.frontend().unsubscribeRedirectUrl()
                        + "/unsubscribe?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8))
                .build();
    }

    /**
     * RFC 8058 one-click target (mail providers POST {@code List-Unsubscribe=One-Click} with the token in the
     * query) and the confirm page's button. The token is the only authentication; 200 with no redirect, as RFC 8058 requires.
     */
    @PostMapping
    public ResponseEntity<Void> unsubscribe(@RequestParam String token) {
        suppressionService.unsubscribe(unsubscribeTokenCodec.read(token));
        return ResponseEntity.ok().build();
    }
}
