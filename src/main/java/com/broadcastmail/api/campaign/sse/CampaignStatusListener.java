package com.broadcastmail.api.campaign.sse;

import com.broadcastmail.api.campaign.dto.CampaignStatusEvent;
import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.campaign.CampaignStatus;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class CampaignStatusListener implements InitializingBean, DisposableBean {

    private static final Pattern CAMPAIGN_ID = Pattern.compile("\"campaignId\":\"([^\"]+)\"");
    private static final Pattern ACCOUNT_ID  = Pattern.compile("\"accountId\":\"([^\"]+)\"");

    private final DataSource dataSource;
    private final CampaignSseRegistry registry;
    private final CampaignRepository campaignRepository;

    public CampaignStatusListener(@Qualifier("directDataSource") DataSource dataSource,
                                   CampaignSseRegistry registry,
                                   CampaignRepository campaignRepository) {
        this.dataSource = dataSource;
        this.registry = registry;
        this.campaignRepository = campaignRepository;
    }

    private volatile boolean running = true;
    private Thread listenerThread;

    @Override
    public void afterPropertiesSet() {
        listenerThread = Thread.ofVirtual().name("pg-notify-listener").start(this::listenLoop);
    }

    @Override
    public void destroy() {
        running = false;
        listenerThread.interrupt();
    }

    private void listenLoop() {
        while (running) {
            try (Connection conn = dataSource.getConnection()) {
                pollConnection(conn);
            } catch (Exception e) {
                if (running) {
                    log.warn("LISTEN connection dropped, reconnecting in 2s: {}", e.getMessage());
                    try { Thread.sleep(2000); } catch (InterruptedException _) { Thread.currentThread().interrupt(); }
                }
            }
        }
    }

    private void pollConnection(Connection conn) throws Exception {
        PGConnection pgConn = conn.unwrap(PGConnection.class);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("LISTEN campaign_status");
        }
        log.info("Listening on campaign_status channel");
        while (running) {
            PGNotification[] notifications = pgConn.getNotifications(5000);
            if (notifications != null) {
                for (PGNotification n : notifications) {
                    dispatch(n.getParameter());
                }
            }
        }
    }

    private void dispatch(String payload) {
        UUID campaignId = extractUuid(CAMPAIGN_ID, payload);
        UUID accountId  = extractUuid(ACCOUNT_ID, payload);
        if (campaignId == null || accountId == null) return;

        Optional<Campaign> campaign = campaignRepository.findById(campaignId);
        if (campaign.isEmpty()) return;

        CampaignStatusEvent event = CampaignStatusEvent.from(campaign.get());

        SseEmitter campaignEmitter = registry.getCampaignEmitter(campaignId);
        if (campaignEmitter != null) {
            send(campaignEmitter, event);
            if (isTerminal(event.status())) campaignEmitter.complete();
        }

        Set<SseEmitter> accountEmitters = registry.getAccountEmitters(accountId);
        for (SseEmitter emitter : accountEmitters) {
            send(emitter, event);
            if (isTerminal(event.status())) emitter.complete();
        }
    }

    private void send(SseEmitter emitter, CampaignStatusEvent event) {
        try {
            emitter.send(SseEmitter.event().name("status").data(event));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private boolean isTerminal(CampaignStatus status) {
        return status == CampaignStatus.SENT || status == CampaignStatus.FAILED || status == CampaignStatus.PARTIALLY_FAILED;
    }

    private UUID extractUuid(Pattern pattern, String json) {
        Matcher m = pattern.matcher(json);
        return m.find() ? UUID.fromString(m.group(1)) : null;
    }
}
