package com.broadcastemail.api.support;

import com.broadcastemail.api.account.Account;
import com.broadcastemail.api.campaign.Campaign;
import com.broadcastemail.api.campaign.CampaignStatus;
import com.broadcastemail.api.campaign.confirm.dto.RecipientRow;
import com.broadcastemail.api.common.SecurityUtil;
import com.broadcastemail.api.connection.Connection;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Object Mother for the campaign test suite: shared defaults for the entities its
 * tests build most, with builders returned (not built objects) so callers can override
 * only the fields their scenario cares about.
 *
 * @see <a href="https://martinfowler.com/bliki/ObjectMother.html">Object Mother</a>
 */
public final class CampaignTestFixtures {

    public static final String TEST_API_KEY = "test-api-key";

    private CampaignTestFixtures() {
    }

    public static Account.AccountBuilder account() {
        return Account.builder()
                .email("test@example.com")
                .passwordHash("")
                .apiKeyHash(SecurityUtil.sha256(TEST_API_KEY))
                .plan("free")
                .emailVerified(true)
                .uniqueRecipientsThisPeriod(0)
                .periodResetAt(OffsetDateTime.now());
    }

    public static Connection.ConnectionBuilder connection(UUID accountId) {
        return Connection.builder()
                .accountId(accountId)
                .name("Test Connection")
                .type("supabase")
                .projectRef("test-ref")
                .projectUrl("https://test.supabase.co")
                .encryptedCreds("encrypted-creds")
                .userTableSchema("public")
                .userTableName("profiles")
                .emailColumn("email")
                .userIdColumn("id");
    }

    public static Campaign.CampaignBuilder draftCampaign(UUID accountId, UUID connectionId) {
        return Campaign.builder()
                .accountId(accountId)
                .connectionId(connectionId)
                .name("Test Campaign")
                .subject("Hello")
                .bodyHtml("<p>Hi</p>")
                .status(CampaignStatus.DRAFT);
    }

    public static List<RecipientRow> recipientRows(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> new RecipientRow("user-id-" + i, "user" + i + "@example.com"))
                .toList();
    }
}
