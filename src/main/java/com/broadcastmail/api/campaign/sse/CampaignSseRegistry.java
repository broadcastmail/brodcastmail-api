package com.broadcastmail.api.campaign.sse;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class CampaignSseRegistry {

    private final ConcurrentHashMap<UUID, SseEmitter> byCampaign = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Set<SseEmitter>> byAccount = new ConcurrentHashMap<>();

    public void registerCampaign(UUID campaignId, SseEmitter emitter) {
        byCampaign.put(campaignId, emitter);
        emitter.onCompletion(() -> byCampaign.remove(campaignId, emitter));
        emitter.onTimeout(() -> byCampaign.remove(campaignId, emitter));
    }

    public void registerAccount(UUID accountId, SseEmitter emitter) {
        byAccount.computeIfAbsent(accountId, _ -> new CopyOnWriteArraySet<>()).add(emitter);
        emitter.onCompletion(() -> deregisterAccount(accountId, emitter));
        emitter.onTimeout(() -> deregisterAccount(accountId, emitter));
    }

    public SseEmitter getCampaignEmitter(UUID campaignId) {
        return byCampaign.get(campaignId);
    }

    public Set<SseEmitter> getAccountEmitters(UUID accountId) {
        return byAccount.getOrDefault(accountId, Set.of());
    }

    /**
     * A comment line every 30 s on every open stream. Streams only send on status changes, and proxies such as
     * Cloudflare close a connection that has been idle for about 100 s. A stream whose client is gone fails the
     * send and is dropped here, instead of waiting for its 5-minute timeout.
     */
    @Scheduled(fixedRate = 30_000)
    public void heartbeat() {
        List<SseEmitter> emitters = new ArrayList<>(byCampaign.values());
        byAccount.values().forEach(emitters::addAll);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (Exception _) {
                drop(emitter);
                emitter.completeWithError(new IllegalStateException("SSE client gone"));
            }
        }
    }

    private void drop(SseEmitter emitter) {
        byCampaign.values().remove(emitter);
        byAccount.forEach((accountId, emitters) -> deregisterAccount(accountId, emitter));
    }

    private void deregisterAccount(UUID accountId, SseEmitter emitter) {
        Set<SseEmitter> emitters = byAccount.get(accountId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) byAccount.remove(accountId, emitters);
        }
    }
}
