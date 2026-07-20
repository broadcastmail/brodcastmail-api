package com.broadcastemail.api.resend;

import com.broadcastemail.api.common.exceptions.InvalidResendApiKeyException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ResendClient {

    private final RestClient resendRestClient;

    public ResendClient(@Qualifier("resendRestClient") RestClient resendRestClient) {
        this.resendRestClient = resendRestClient;
    }

    public void validateApiKey(String apiKey)
    {
        if (apiKey == null || apiKey.isBlank()) {
            throw new InvalidResendApiKeyException("Resend API key is required");
        }
        if (!apiKey.startsWith("re_")) {
            throw new InvalidResendApiKeyException("Invalid Resend API key format — must start with re_");
        }
        if (apiKey.length() < 10) {
            throw new InvalidResendApiKeyException("Invalid Resend API key format — too short");
        }
        resendRestClient
                .get()
                .uri("/domains")
                .header("Authorization", "Bearer " + apiKey)
                .retrieve()
                .onStatus(status -> status == HttpStatus.UNAUTHORIZED,
                        (request, response) -> {
                            throw new InvalidResendApiKeyException(
                                    "Invalid or revoked Resend API key"
                            );
                        })
                .onStatus(status -> status == HttpStatus.FORBIDDEN,
                        (request, response) -> {
                            throw new InvalidResendApiKeyException(
                                    "Resend API key lacks required permissions"
                            );
                        })
                .toBodilessEntity();
    }

}
