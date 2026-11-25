package com.broadcastmail.api.suppressions;

import com.broadcastmail.api.IntegrationTest;
import com.broadcastmail.api.TestContainersConfiguration;
import com.broadcastmail.api.config.AppProperties;
import com.broadcastmail.common.unsubscribe.UnsubscribeClaims;
import com.broadcastmail.common.unsubscribe.UnsubscribeTokenCodec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestContainersConfiguration.class)
class SuppressionsControllerTest extends IntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private AppProperties appProperties;

    @MockitoBean
    private SuppressionService suppressionService;

    @Test
    void getSendsToTheConfirmPageWithoutUnsubscribing() {
        // Given
        String token = issue(UUID.randomUUID(), UUID.randomUUID(), "a@b.c");

        // When
        var response = mockMvc.get()
                .uri("/unsubscribe")
                .param("token", token)
                .exchange();

        // Then
        assertThat(response).hasStatus(302);
        assertThat(response.getResponse().getHeader("Location"))
                .isEqualTo(appProperties.frontend().unsubscribeRedirectUrl() + "/unsubscribe?token="
                        + URLEncoder.encode(token, StandardCharsets.UTF_8));
        verifyNoInteractions(suppressionService);
    }

    @Test
    void oneClickPostUnsubscribesWithoutAuthOrCsrf() {
        // Given
        UUID recipientId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = issue(recipientId, accountId, "a@b.c");

        // When — what Gmail/Yahoo send per RFC 8058: token in the query, fixed form body
        var response = mockMvc.post()
                .uri("/unsubscribe?token={token}", token)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("List-Unsubscribe=One-Click")
                .exchange();

        // Then
        assertThat(response).hasStatus(200);
        assertThat(response.getResponse().getHeader("Location")).isNull();
        verify(suppressionService).unsubscribe(new UnsubscribeClaims(recipientId, accountId, "a@b.c"));
    }

    @Test
    void postReturns401AndDoesNothingForAnInvalidToken() {
        // When
        var response = mockMvc.post()
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
        verifyNoInteractions(suppressionService);
    }

    @Test
    void postReturns401ForATokenFromAnotherSecret() {
        // When
        var response = mockMvc.post()
                .uri("/unsubscribe")
                .param("token", new UnsubscribeTokenCodec("00000000000000000000000000000000")
                        .issue(new UnsubscribeClaims(UUID.randomUUID(), UUID.randomUUID(), "a@b.c")))
                .exchange();

        // Then
        assertThat(response).hasStatus(401);
        verifyNoInteractions(suppressionService);
    }

    private String issue(UUID recipientId, UUID accountId, String email) {
        return new UnsubscribeTokenCodec(appProperties.unsubscribeSecret())
                .issue(new UnsubscribeClaims(recipientId, accountId, email));
    }
}
