package com.broadcastmail.api.resend;

import com.broadcastmail.api.common.exceptions.InvalidResendApiKeyException;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.webhooks.model.CreateWebhookOptions;
import com.resend.services.webhooks.model.CreateWebhookResponseSuccess;
import com.resend.services.webhooks.model.WebhookEvent;
import org.springframework.stereotype.Component;

@Component
public class ResendClient {

    public void validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new InvalidResendApiKeyException("Resend API key is required");
        }
        if (!apiKey.startsWith("re_")) {
            throw new InvalidResendApiKeyException("Invalid Resend API key format — must start with re_");
        }
        if (apiKey.length() < 10) {
            throw new InvalidResendApiKeyException("Invalid Resend API key format — too short");
        }
        try {
            Resend resend = new Resend(apiKey);
            resend.domains().list();
        } catch (ResendException _) {
            throw new InvalidResendApiKeyException("Invalid or revoked Resend API key");
        }
    }


    public RegisteredWebhook registerWebhook(String apiKey, String endpointUrl)
    {
        try{
            Resend resend = new Resend(apiKey);
            CreateWebhookOptions options = CreateWebhookOptions.builder()
                    .endpoint(endpointUrl)
                    .events(
                            WebhookEvent.EMAIL_SENT,
                            WebhookEvent.EMAIL_DELIVERED,
                            WebhookEvent.EMAIL_OPENED,
                            WebhookEvent.EMAIL_BOUNCED,
                            WebhookEvent.EMAIL_FAILED
                    )
                    .build();
            CreateWebhookResponseSuccess response= resend.webhooks().create(options);
            return new RegisteredWebhook(response.getId(), response.getSigningSecret());
        }
        catch (ResendException _)
        {
            throw new InvalidResendApiKeyException("Error occurred while registering webhook");
        }
    }

    public record RegisteredWebhook(String id, String signingSecret) {}

}
