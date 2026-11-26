package com.broadcastmail.api.account.plan;

import com.broadcastmail.api.IntegrationTest;
import com.broadcastmail.api.TestContainersConfiguration;
import com.broadcastmail.api.billing.BillingService;
import com.broadcastmail.api.support.CampaignTestFixtures;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import com.broadcastmail.common.account.plan.PlanLimitExceededException;
import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.campaign.CampaignStatus;
import com.broadcastmail.common.campaign.recipient.CampaignRecipient;
import com.broadcastmail.common.campaign.recipient.CampaignRecipientRepository;
import com.broadcastmail.common.campaign.recipient.RecipientStatus;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import com.broadcastmail.common.usage.RecipientUsage;
import com.broadcastmail.common.usage.RecipientUsageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PlanFacade against a real Postgres, Flyway migrations included: the 30-day limit must not depend on campaigns that retention deletes. */
@SpringBootTest
@Import(TestContainersConfiguration.class)
class PlanFacadeIntegrationTest extends IntegrationTest {

    @Autowired private PlanFacade planFacade;
    @Autowired private AccountRepository accountRepository;
    @Autowired private ConnectionRepository connectionRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private RecipientUsageRepository recipientUsageRepository;
    @Autowired private CampaignRecipientRepository campaignRecipientRepository;
    @MockitoBean private BillingService billingService;

    private Account account;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        account = accountRepository.save(CampaignTestFixtures.account().build());
        Connection connection = connectionRepository.save(CampaignTestFixtures.connection(account.getId()).build());
        campaign = campaignRepository.save(CampaignTestFixtures.draftCampaign(account.getId(), connection.getId())
                .status(CampaignStatus.SENT).build());
    }

    @AfterEach
    void tearDown() {
        recipientUsageRepository.deleteAll();
        campaignRecipientRepository.deleteAll();
        campaignRepository.deleteAll();
        connectionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void freeLimitIsStillReachedAfterTheCampaignThatUsedItWasDeleted() {
        // Given: 500 addresses emailed 8 days ago by a campaign that retention has since deleted
        insertUsage(500, 8);
        campaignRepository.deleteById(campaign.getId());

        // Then
        assertThatThrownBy(() -> planFacade.enforceRecipientLimit(account, 1))
                .isInstanceOf(PlanLimitExceededException.class);
    }

    @Test
    void boundaryAt499Plus1PassesAndAt500Plus1Fails() {
        insertUsage(499, 1);
        assertThatCode(() -> planFacade.enforceRecipientLimit(account, 1)).doesNotThrowAnyException();

        insertUsage(1, 1, "one-more");
        assertThatThrownBy(() -> planFacade.enforceRecipientLimit(account, 1))
                .isInstanceOf(PlanLimitExceededException.class);
    }

    @Test
    void addressesOlderThan30DaysNoLongerCount() {
        insertUsage(500, 31);

        assertThatCode(() -> planFacade.enforceRecipientLimit(account, 1)).doesNotThrowAnyException();
    }

    @Test
    void retryCountsOnlyFailedAddressesNotEmailedThisPeriod() {
        // Given: four failed recipients, one sent one
        failedRecipient("used-recently@example.com");   // counted 1 day ago → free
        failedRecipient("used-long-ago@example.com");   // counted 31 days ago → new again
        failedRecipient("never-used@example.com");      // → new
        failedRecipient("other-account@example.com");   // only another account counted it → new
        recipient("sent-never-used@example.com", RecipientStatus.SENT, "u5");  // not failed → not retried
        recipientUsageRepository.saveAll(List.of(
                new RecipientUsage(account.getId(), "used-recently@example.com", OffsetDateTime.now().minusDays(1)),
                new RecipientUsage(account.getId(), "used-long-ago@example.com", OffsetDateTime.now().minusDays(31)),
                new RecipientUsage(otherAccount().getId(), "other-account@example.com", OffsetDateTime.now().minusDays(1))));

        // When
        long fresh = campaignRecipientRepository.countEmailsNotUsedSince(
                campaign.getId(), RecipientStatus.FAILED, account.getId(), OffsetDateTime.now().minusDays(30));

        // Then: the long-ago, never-used and other-account addresses
        assertThat(fresh).isEqualTo(3);
    }

    @Test
    void retryAtTheLimitPassesWhenEveryFailedAddressIsAlreadyCounted() {
        // Given: 500 used, and the only failed address is one of them
        insertUsage(499, 1);
        failedRecipient("addr-again@example.com");
        recipientUsageRepository.save(new RecipientUsage(account.getId(), "addr-again@example.com", OffsetDateTime.now()));

        // When / Then
        assertThatCode(() -> planFacade.enforceRetryLimit(account, campaign.getId())).doesNotThrowAnyException();

        // Given: one more failed address that was never emailed
        failedRecipient("new@example.com");

        // When / Then: 501
        assertThatThrownBy(() -> planFacade.enforceRetryLimit(account, campaign.getId()))
                .isInstanceOf(PlanLimitExceededException.class);
    }

    private int recipients;

    private void failedRecipient(String email) {
        recipient(email, RecipientStatus.FAILED, "u" + (++recipients));
    }

    private void recipient(String email, RecipientStatus status, String userId) {
        campaignRecipientRepository.save(CampaignRecipient.builder()
                .campaignId(campaign.getId())
                .externalUserId(userId)
                .email(email)
                .status(status)
                .idempotencyKey(campaign.getId() + ":" + userId)
                .build());
    }

    private Account otherAccount() {
        return accountRepository.save(CampaignTestFixtures.account().email("other@example.com").build());
    }

    private void insertUsage(int count, int daysAgo) {
        insertUsage(count, daysAgo, "addr");
    }

    private void insertUsage(int count, int daysAgo, String prefix) {
        OffsetDateTime firstUsedAt = OffsetDateTime.now().minusDays(daysAgo);
        recipientUsageRepository.saveAll(IntStream.rangeClosed(1, count)
                .mapToObj(i -> new RecipientUsage(account.getId(), prefix + i + "@example.com", firstUsedAt))
                .toList());
    }
}
