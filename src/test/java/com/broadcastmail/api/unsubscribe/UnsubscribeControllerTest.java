package com.broadcastmail.api.unsubscribe;

import com.broadcastmail.api.TestContainersConfiguration;
import com.broadcastmail.api.config.AppProperties;
import com.broadcastmail.common.campaign.recipient.CampaignRecipientRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestContainersConfiguration.class)
class UnsubscribeControllerTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private AppProperties appProperties;

    @MockitoBean
    private CampaignRecipientRepository campaignRecipientRepository;

    @Test
    void shouldRedirectToFrontendAndMarkRecipientUnsubscribedWhenTokenIsValid() {
        // Given
        UUID recipientId = UUID.randomUUID();
        String token = validTokenFor(recipientId);

        // When
        var response = mockMvc.get()
                .uri("/unsubscribe")
                .param("token", token)
                .exchange();

        // Then
        assertThat(response).hasStatus(302);
        assertThat(response.getResponse().getHeader("Location"))
                .isEqualTo(appProperties.frontend().url() + "/unsubscribed");
        verify(campaignRecipientRepository).markUnsubscribed(recipientId);
    }

    @Test
    void shouldReturn401AndNotMarkUnsubscribedWhenTokenIsInvalid() {
        // When
        var response = mockMvc.get()
                .uri("/unsubscribe")
                .param("token", "not-a-real-token")
                .exchange();

        // Then
        assertThat(response)
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.error")
                .asString()
                .isEqualTo("Invalid unsubscribe token");
        verifyNoInteractions(campaignRecipientRepository);
    }

    @Test
    void shouldReturn401WhenTokenIsExpired() {
        // Given
        UUID recipientId = UUID.randomUUID();
        String token = Jwts.builder()
                .subject(recipientId.toString())
                .issuedAt(Date.from(Instant.now().minusSeconds(120)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(key())
                .compact();

        // When
        var response = mockMvc.get()
                .uri("/unsubscribe")
                .param("token", token)
                .exchange();

        // Then
        assertThat(response).hasStatus(401);
        verifyNoInteractions(campaignRecipientRepository);
    }

    @Test
    void shouldReturn401WhenTokenIsSignedWithWrongSecret() {
        // Given
        UUID recipientId = UUID.randomUUID();
        SecretKey wrongKey = Keys.hmacShaKeyFor("00000000000000000000000000000000".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject(recipientId.toString())
                .signWith(wrongKey)
                .compact();

        // When
        var response = mockMvc.get()
                .uri("/unsubscribe")
                .param("token", token)
                .exchange();

        // Then
        assertThat(response).hasStatus(401);
        verifyNoInteractions(campaignRecipientRepository);
    }

    private String validTokenFor(UUID recipientId) {
        return Jwts.builder()
                .subject(recipientId.toString())
                .issuedAt(Date.from(Instant.now()))
                .signWith(key())
                .compact();
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(appProperties.unsubscribeSecret().getBytes(StandardCharsets.UTF_8));
    }
}
