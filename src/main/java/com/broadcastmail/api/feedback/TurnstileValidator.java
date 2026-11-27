package com.broadcastmail.api.feedback;

import com.broadcastmail.api.common.exceptions.TurnstileVerificationException;
import com.broadcastmail.api.config.FeedbackProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Slf4j
public class TurnstileValidator {

    private static final String SITEVERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final RestClient restClient = RestClient.create();
    private final FeedbackProperties properties;

    public TurnstileValidator(FeedbackProperties properties) {
        this.properties = properties;
    }

    public void validate(String token) {
        String secret = properties.turnstileSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("[feedback] TURNSTILE_SECRET_KEY is not set; rejecting marketing feedback");
            throw new TurnstileVerificationException();
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secret);
        form.add("response", token);

        SiteverifyResponse response;
        try {
            response = restClient.post()
                    .uri(SITEVERIFY_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(SiteverifyResponse.class);
        } catch (RestClientException e) {
            log.warn("[feedback] Turnstile siteverify failed: {}", e.getMessage());
            throw new TurnstileVerificationException();
        }
        if (response == null || !response.success()) {
            throw new TurnstileVerificationException();
        }
    }

    record SiteverifyResponse(boolean success) {
    }
}
