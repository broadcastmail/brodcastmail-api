package com.broadcastmail.api.campaign.sse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    private void deregisterAccount(UUID accountId, SseEmitter emitter) {
        Set<SseEmitter> emitters = byAccount.get(accountId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) byAccount.remove(accountId, emitters);
        }
    }
}
