package com.broadcastemail.api.resend;

import com.broadcastemail.api.common.exceptions.InvalidResendApiKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import org.springframework.web.client.RestClient;


class ResendClientTest {

    private ResendClient resendClient;

    @BeforeEach
    void setUp() {
        resendClient = new ResendClient(RestClient.builder()
                .baseUrl("https://api.resend.com")
                .build());
    }

    @Test
    void shouldRejectNullResendKey() {
        // Given / When / Then
        assertThatThrownBy(() -> resendClient.validateApiKey(null))
                .isInstanceOf(InvalidResendApiKeyException.class);
    }

    @Test
    void shouldRejectResendKeyWithoutRePrefix() {
        // Given / When / Then
        assertThatThrownBy(() -> resendClient.validateApiKey("sk_invalid_key"))
                .isInstanceOf(InvalidResendApiKeyException.class);
    }

    @Test
    void shouldRejectRevokedResendKey() {
        // Given / When / Then
        assertThatThrownBy(() -> resendClient.validateApiKey("re_short"))
                .isInstanceOf(InvalidResendApiKeyException.class);
    }
}