package com.broadcastmail.api.campaign.sse;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CampaignSseRegistryTest {

    private final CampaignSseRegistry registry = new CampaignSseRegistry();

    @Test
    void heartbeatSendsToCampaignAndAccountStreams() throws IOException {
        // Given
        SseEmitter campaignStream = mock(SseEmitter.class);
        SseEmitter accountStream = mock(SseEmitter.class);
        registry.registerCampaign(UUID.randomUUID(), campaignStream);
        registry.registerAccount(UUID.randomUUID(), accountStream);

        // When
        registry.heartbeat();

        // Then
        verify(campaignStream).send(any(SseEmitter.SseEventBuilder.class));
        verify(accountStream).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void heartbeatDropsStreamsWhoseClientIsGone() throws IOException {
        // Given
        UUID campaignId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        SseEmitter goneCampaign = mock(SseEmitter.class);
        SseEmitter goneAccount = mock(SseEmitter.class);
        doThrow(new IOException("broken pipe")).when(goneCampaign).send(any(SseEmitter.SseEventBuilder.class));
        doThrow(new IOException("broken pipe")).when(goneAccount).send(any(SseEmitter.SseEventBuilder.class));
        registry.registerCampaign(campaignId, goneCampaign);
        registry.registerAccount(accountId, goneAccount);

        // When
        registry.heartbeat();

        // Then
        assertThat(registry.getCampaignEmitter(campaignId)).isNull();
        assertThat(registry.getAccountEmitters(accountId)).isEmpty();
        verify(goneCampaign).completeWithError(any());
    }
}
