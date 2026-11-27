package com.broadcastmail.api.billing;

import com.broadcastmail.api.IntegrationTest;
import com.broadcastmail.api.TestContainersConfiguration;
import com.broadcastmail.api.TestSecurityConfig;
import com.broadcastmail.api.support.CampaignTestFixtures;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import com.broadcastmail.common.session.SessionRepository;
import com.stripe.exception.StripeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockCookie;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestContainersConfiguration.class, TestSecurityConfig.class})
@ActiveProfiles("test")
class BillingControllerTest extends IntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private SessionRepository sessionRepository;

    @MockitoBean
    private BillingService billingService;

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";

    private Account account;

    @BeforeEach
    void setUp() {
        account = accountRepository.save(CampaignTestFixtures.account().build());
        sessionRepository.save(CampaignTestFixtures.session(account.getId()).build());
    }

    @AfterEach
    void tearDown() {
        accountRepository.deleteAll();
    }

    @Test
    void shouldReturnCheckoutUrlOnSuccess() throws StripeException {
        // Given
        when(billingService.createCheckoutSession(any(UUID.class), anyBoolean(), any()))
                .thenReturn("https://checkout.stripe.com/pay/test123");

        // When
        var response = mockMvc.post()
                .uri("/api/v1/billing/checkout")
                .param("redirectOrigin", ALLOWED_ORIGIN)
                .cookie(new MockCookie("bm_session", CampaignTestFixtures.TEST_SESSION_TOKEN))
                .exchange();

        // Then
        assertThat(response).hasStatus(200);
        assertThat(response).bodyJson()
                .extractingPath("$.url")
                .asString()
                .isEqualTo("https://checkout.stripe.com/pay/test123");
    }

    @Test
    void shouldReturn401WhenNoApiKey() {
        // When
        var response = mockMvc.post()
                .uri("/api/v1/billing/checkout")
                .exchange();

        // Then
        assertThat(response).hasStatus(401);
    }

    @Test
    void shouldReturnPortalUrlOnSuccess() throws StripeException {
        // Given
        account.setStripeCustomerId("cus_test123");
        accountRepository.save(account);
        when(billingService.createPortalSession(any(), any()))
                .thenReturn("https://billing.stripe.com/session/test123");

        // When
        var response = mockMvc.post()
                .uri("/api/v1/billing/portal")
                .param("redirectOrigin", ALLOWED_ORIGIN)
                .cookie(new MockCookie("bm_session", CampaignTestFixtures.TEST_SESSION_TOKEN))
                .exchange();

        // Then
        assertThat(response).hasStatus(200);
        assertThat(response).bodyJson()
                .extractingPath("$.url")
                .asString()
                .isEqualTo("https://billing.stripe.com/session/test123");
    }

    @Test
    void shouldReturn400WhenNoStripeCustomer() {
        // Given — account has no stripeCustomerId

        // When
        var response = mockMvc.post()
                .uri("/api/v1/billing/portal")
                .param("redirectOrigin", ALLOWED_ORIGIN)
                .cookie(new MockCookie("bm_session", CampaignTestFixtures.TEST_SESSION_TOKEN))
                .exchange();

        // Then
        assertThat(response).hasStatus(400);
    }

    @Test
    void shouldReturn400WhenCheckoutHasNoRedirectOrigin() {
        // Given — an authenticated account

        // When
        var response = mockMvc.post()
                .uri("/api/v1/billing/checkout")
                .cookie(new MockCookie("bm_session", CampaignTestFixtures.TEST_SESSION_TOKEN))
                .exchange();

        // Then
        assertThat(response).hasStatus(400);
    }

    @Test
    void shouldReturn400WhenPortalHasNoRedirectOrigin() {
        // Given
        account.setStripeCustomerId("cus_test123");
        accountRepository.save(account);

        // When
        var response = mockMvc.post()
                .uri("/api/v1/billing/portal")
                .cookie(new MockCookie("bm_session", CampaignTestFixtures.TEST_SESSION_TOKEN))
                .exchange();

        // Then
        assertThat(response).hasStatus(400);
    }
}
