package com.broadcastmail.api.feedback;

import com.broadcastmail.api.IntegrationTest;
import com.broadcastmail.api.TestContainersConfiguration;
import com.broadcastmail.api.TestSecurityConfig;
import com.broadcastmail.api.common.exceptions.TurnstileVerificationException;
import com.broadcastmail.api.resend.ResendClient;
import com.broadcastmail.api.support.CampaignTestFixtures;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import com.broadcastmail.common.session.SessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestContainersConfiguration.class, TestSecurityConfig.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.feedback.resend-api-key=re_test_key",
        "app.feedback.from=feedback@example.com",
        "app.feedback.to=team@example.com"})
class FeedbackControllerTest extends IntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private FeedbackRepository feedbackRepository;

    @MockitoBean
    private TurnstileValidator turnstileValidator;
    @MockitoBean
    private ResendClient resendClient;

    private Account account;

    @BeforeEach
    void setUp() {
        account = accountRepository.save(CampaignTestFixtures.account().build());
        sessionRepository.save(CampaignTestFixtures.session(account.getId()).build());
    }

    @AfterEach
    void tearDown() {
        feedbackRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void shouldStoreSignedInFeedbackWithTheAccountAndItsPlan() {
        // Given
        String body = """
                {"message": "  The audience preview is slow  ", "source": "app"}""";

        // When
        var response = mockMvc.post()
                .uri("/api/v1/feedback")
                .cookie(new MockCookie("bm_session", CampaignTestFixtures.TEST_SESSION_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();

        // Then
        assertThat(response).hasStatus(204);
        assertThat(feedbackRepository.findAll()).singleElement().satisfies(feedback -> {
            assertThat(feedback.getAccountId()).isEqualTo(account.getId());
            assertThat(feedback.getPlan()).isEqualTo("FREE");
            assertThat(feedback.getSource()).isEqualTo("app");
            assertThat(feedback.getMessage()).isEqualTo("The audience preview is slow");
            assertThat(feedback.getEmail()).isNull();
        });
    }

    @Test
    void shouldRejectSignedInFeedbackWithAnUnknownSource() {
        String body = """
                {"message": "Hi", "source": "marketing"}""";

        // When
        var response = mockMvc.post()
                .uri("/api/v1/feedback")
                .cookie(new MockCookie("bm_session", CampaignTestFixtures.TEST_SESSION_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();

        // Then
        assertThat(response).hasStatus(400);
        assertThat(feedbackRepository.findAll()).isEmpty();
    }

    @Test
    void shouldStoreMarketingFeedbackWithoutAnAccount() {
        // Given
        String body = """
                {"message": "Welcome emails", "email": "visitor@example.com", "page": "/compare/loops",
                 "turnstileToken": "token"}""";

        // When: no session cookie
        var response = mockMvc.post()
                .uri("/api/v1/public/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();

        // Then
        assertThat(response).hasStatus(204);
        assertThat(feedbackRepository.findAll()).singleElement().satisfies(feedback -> {
            assertThat(feedback.getAccountId()).isNull();
            assertThat(feedback.getPlan()).isNull();
            assertThat(feedback.getSource()).isEqualTo("marketing");
            assertThat(feedback.getPage()).isEqualTo("/compare/loops");
            assertThat(feedback.getEmail()).isEqualTo("visitor@example.com");
        });
    }

    @Test
    void shouldNotStoreMarketingFeedbackWhenTurnstileRejectsTheToken() {
        // Given
        doThrow(new TurnstileVerificationException()).when(turnstileValidator).validate("bad-token");
        String body = """
                {"message": "Spam", "turnstileToken": "bad-token"}""";

        // When
        var response = mockMvc.post()
                .uri("/api/v1/public/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();

        // Then
        assertThat(response).hasStatus(403);
        assertThat(feedbackRepository.findAll()).isEmpty();
    }
}
