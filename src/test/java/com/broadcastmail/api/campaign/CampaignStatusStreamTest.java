package com.broadcastmail.api.campaign;

import com.broadcastmail.api.TestContainersConfiguration;
import com.broadcastmail.api.TestSecurityConfig;
import com.broadcastmail.api.support.CampaignTestFixtures;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.campaign.CampaignStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static com.broadcastmail.api.support.CampaignTestFixtures.TEST_API_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestContainersConfiguration.class, TestSecurityConfig.class})
class CampaignStatusStreamTest {

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @MockitoBean private CampaignRepository campaignRepository;

    private WebTestClient webTestClient;
    private Account account;

    private static final UUID CAMPAIGN_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
        account = accountRepository.save(CampaignTestFixtures.account().build());
    }

    @AfterEach
    void tearDown() {
        accountRepository.deleteAll();
    }

    @Test
    @Timeout(10)
    void shouldStreamStatusEventsForTerminalCampaign() {
        Campaign campaign = CampaignTestFixtures.draftCampaign(account.getId(), UUID.randomUUID())
                .id(CAMPAIGN_ID)
                .status(CampaignStatus.SENT)
                .recipientCount(100)
                .deliveredCount(95)
                .build();

        when(campaignRepository.findByAccountIdAndId(any(), eq(CAMPAIGN_ID)))
                .thenReturn(Optional.of(campaign));

        String event = webTestClient
                .get()
                .uri("/api/v1/campaigns/" + CAMPAIGN_ID + "/status/stream")
                .header("Cookie", "bm_session=" + TEST_API_KEY)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseBody()
                .blockFirst(Duration.ofSeconds(5));

        assertThat(event).isNotNull().contains("SENT");
    }

    @Test
    @Timeout(10)
    void shouldStreamStatusEventsForActiveCampaign() {
        Campaign campaign = CampaignTestFixtures.draftCampaign(account.getId(), UUID.randomUUID())
                .id(CAMPAIGN_ID)
                .status(CampaignStatus.SENDING)
                .recipientCount(100)
                .deliveredCount(50)
                .sentCount(60)
                .build();

        when(campaignRepository.findByAccountIdAndId(any(), eq(CAMPAIGN_ID)))
                .thenReturn(Optional.of(campaign));

        String event = webTestClient
                .get()
                .uri("/api/v1/campaigns/" + CAMPAIGN_ID + "/status/stream")
                .header("Cookie", "bm_session=" + TEST_API_KEY)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseBody()
                .blockFirst(Duration.ofSeconds(5));

        assertThat(event).isNotNull().contains("SENDING");
    }
}
