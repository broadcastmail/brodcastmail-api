package com.broadcastmail.api.billing;

import com.broadcastmail.api.TestContainersConfiguration;
import com.broadcastmail.api.support.CampaignTestFixtures;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import com.stripe.exception.StripeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestContainersConfiguration.class)
class BillingControllerTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @MockitoBean
    private BillingService billingService;

    private Account account;

    @BeforeEach
    void setUp() {
        account = accountRepository.save(CampaignTestFixtures.account().build());
    }

    @AfterEach
    void tearDown() {
        accountRepository.deleteAll();
    }

    @Test
    void shouldReturnCheckoutUrlOnSuccess() throws StripeException {
        // Given
        when(billingService.createCheckoutSession(any(UUID.class)))
                .thenReturn("https://checkout.stripe.com/pay/test123");

        // When
        var response = mockMvc.post()
                .uri("/api/v1/billing/checkout")
                .header("X-API-Key", CampaignTestFixtures.TEST_API_KEY)
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
        when(billingService.createPortalSession(any()))
                .thenReturn("https://billing.stripe.com/session/test123");

        // When
        var response = mockMvc.post()
                .uri("/api/v1/billing/portal")
                .header("X-API-Key", CampaignTestFixtures.TEST_API_KEY)
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
                .header("X-API-Key", CampaignTestFixtures.TEST_API_KEY)
                .exchange();

        // Then
        assertThat(response).hasStatus(400);
    }
}