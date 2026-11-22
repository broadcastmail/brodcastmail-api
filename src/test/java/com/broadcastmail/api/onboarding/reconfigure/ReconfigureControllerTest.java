package com.broadcastmail.api.onboarding.reconfigure;

import com.broadcastmail.api.IntegrationTest;
import com.broadcastmail.api.TestContainersConfiguration;
import com.broadcastmail.api.TestSecurityConfig;
import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.common.exceptions.InvalidOnboardingSessionException;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.connection.dto.DetectedColumn;
import com.broadcastmail.api.oauth.OAuthSessionStore;
import com.broadcastmail.api.oauth.token.OAuthToken;
import com.broadcastmail.api.oauth.token.OAuthTokenRepository;
import com.broadcastmail.api.onboarding.OnboardingSession;
import com.broadcastmail.api.supabase.management.SupabaseManagementClient;
import com.broadcastmail.api.supabase.reader.ReaderConnectionVerifier;
import com.broadcastmail.api.support.CampaignTestFixtures;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import com.broadcastmail.common.campaign.filter.FilterSource;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import com.broadcastmail.common.filterablecolumn.FilterableColumn;
import com.broadcastmail.common.filterablecolumn.FilterableColumnRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static com.broadcastmail.api.support.CampaignTestFixtures.TEST_API_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestContainersConfiguration.class, TestSecurityConfig.class})
@ActiveProfiles("test")
class ReconfigureControllerTest extends IntegrationTest {

    private static final String ORIGIN = "http://localhost:3000";
    private static final String OLD_REF = "test-ref";
    private static final String OLD_ROLE = "broadcastmail_r_test";
    private static final String NEW_REF = "new-ref";
    private static final String NEW_ROLE = "broadcastmail_r_new";
    private static final String OWNER_EMAIL = "owner@example.com";
    private static final String ACCESS_TOKEN = "access-token";

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private OAuthSessionStore onboardingSessionStore;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ConnectionRepository connectionRepository;

    @Autowired
    private FilterableColumnRepository filterableColumnRepository;

    @Autowired
    private OAuthTokenRepository oAuthTokenRepository;

    @Autowired
    private EncryptionProperties encryptionProperties;

    @MockitoBean
    private SupabaseManagementClient supabaseManagementClient;

    @MockitoBean
    private ReaderConnectionVerifier readerConnectionVerifier;

    private Account account;
    private Connection connection;

    @BeforeEach
    void setUp() {
        account = accountRepository.saveAndFlush(CampaignTestFixtures.account().build());
        connection = connectionRepository.saveAndFlush(CampaignTestFixtures.connection(account.getId()).build());
        filterableColumnRepository.saveAndFlush(FilterableColumn.builder()
                .connectionId(connection.getId()).columnName("old_col").columnType("text").displayName("old_col")
                .source(FilterSource.PROFILE_TABLE).enabled(true).cardinalityWarning(false).build());
        oAuthTokenRepository.saveAndFlush(OAuthToken.builder()
                .accountId(account.getId())
                .accessToken("old-access")
                .refreshToken("old-refresh")
                .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1))
                .build());
    }

    @AfterEach
    void tearDown() {
        filterableColumnRepository.deleteAll();
        connectionRepository.deleteAll();
        oAuthTokenRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void shouldReturn204AndClearOnboardingCookieWhenReconfigureSucceeds() {
        // Given
        String sessionToken = createSession(OLD_REF, OLD_ROLE, OWNER_EMAIL);

        // When
        var response = authedReconfigure(sessionToken).exchange();

        // Then
        assertThat(response).hasStatus(204);
        assertThat(response.getResponse().getHeader("Location")).isNull();
        assertThat(response.getResponse().getHeader("Set-Cookie")).startsWith("onboarding_session=;");
    }

    @Test
    void shouldKeepRoleAndRegrantProfileTableWhenProjectIsUnchanged() {
        // Given
        String sessionToken = createSession(OLD_REF, OLD_ROLE, OWNER_EMAIL);

        // When
        authedReconfigure(sessionToken).exchange();

        // Then
        Connection updated = connectionRepository.findById(connection.getId()).orElseThrow();
        assertThat(updated.getProjectRef()).isEqualTo(OLD_REF);
        assertThat(updated.getDbRole()).isEqualTo(OLD_ROLE);
        assertThat(updated.getUserTableName()).isEqualTo("subscriptions");
        assertThat(updated.getUserIdColumn()).isEqualTo("user_id");
        verify(supabaseManagementClient, never()).executeSql(anyString(), anyString(), contains("DROP OWNED BY"));
        verify(supabaseManagementClient, atLeastOnce()).executeSql(eq(ACCESS_TOKEN), eq(OLD_REF),
                contains("GRANT SELECT ON \"public\".\"subscriptions\" TO " + OLD_ROLE));
    }

    @Test
    void shouldSwitchToNewRoleAndDropOldOneWhenProjectChanges() {
        // Given
        String sessionToken = createSession(NEW_REF, NEW_ROLE, OWNER_EMAIL);

        // When
        authedReconfigure(sessionToken).exchange();

        // Then
        Connection updated = connectionRepository.findById(connection.getId()).orElseThrow();
        assertThat(updated.getProjectRef()).isEqualTo(NEW_REF);
        assertThat(updated.getDbRole()).isEqualTo(NEW_ROLE);
        assertThat(updated.getPoolerPrefix()).isEqualTo("aws-0-eu-west-1");
        assertThat(updated.getEncryptedCreds()).isEqualTo("enc-new-pw");
        verify(supabaseManagementClient).executeSql(eq(ACCESS_TOKEN), eq(OLD_REF), contains("DROP OWNED BY " + OLD_ROLE));
    }

    @Test
    void shouldReplaceColumnsTokensAndOwnerEmailWhenReconfigureSucceeds() {
        // Given
        String sessionToken = createSession(OLD_REF, OLD_ROLE, OWNER_EMAIL);

        // When
        authedReconfigure(sessionToken).exchange();

        // Then
        assertThat(filterableColumnRepository.findByConnectionId(connection.getId()))
                .extracting(FilterableColumn::getColumnName)
                .containsExactly("status");
        OAuthToken stored = oAuthTokenRepository.findByAccountId(account.getId()).orElseThrow();
        assertThat(SecurityUtil.decrypt(stored.getAccessToken(), encryptionProperties.key())).isEqualTo(ACCESS_TOKEN);
        assertThat(stored.getRefreshToken()).isEqualTo("enc-new-refresh");
        assertThat(accountRepository.findById(account.getId()).orElseThrow().getEmail()).isEqualTo(OWNER_EMAIL);
    }

    @Test
    void shouldInvalidateSessionWhenReconfigureSucceeds() {
        // Given
        String sessionToken = createSession(OLD_REF, OLD_ROLE, OWNER_EMAIL);

        // When
        authedReconfigure(sessionToken).exchange();

        // Then
        assertThatThrownBy(() -> onboardingSessionStore.get(sessionToken))
                .isInstanceOf(InvalidOnboardingSessionException.class);
    }

    @Test
    void shouldReturn409AndRollBackWhenOwnerEmailBelongsToAnotherAccount() {
        // Given
        accountRepository.saveAndFlush(CampaignTestFixtures.account()
                .email("taken@example.com")
                .apiKeyHash(SecurityUtil.sha256("other-key"))
                .build());
        String sessionToken = createSession(NEW_REF, NEW_ROLE, "taken@example.com");

        // When
        var response = authedReconfigure(sessionToken).exchange();

        // Then
        assertThat(response).hasStatus(409);
        Connection unchanged = connectionRepository.findById(connection.getId()).orElseThrow();
        assertThat(unchanged.getProjectRef()).isEqualTo(OLD_REF);
        assertThat(unchanged.getDbRole()).isEqualTo(OLD_ROLE);
        assertThat(filterableColumnRepository.findByConnectionId(connection.getId()))
                .extracting(FilterableColumn::getColumnName)
                .containsExactly("old_col");
        assertThat(oAuthTokenRepository.findByAccountId(account.getId()).orElseThrow().getAccessToken())
                .isEqualTo("old-access");
    }

    @Test
    void shouldReturn401WhenSessionIsNotAReconfigure() {
        // Given
        String sessionToken = onboardingSessionStore.create(
                baseSession(OLD_REF, OLD_ROLE, OWNER_EMAIL).accountId(null).build());

        // When
        var response = authedReconfigure(sessionToken).exchange();

        // Then
        assertThat(response).hasStatus(401);
    }

    @Test
    void shouldReturn401WhenOnboardingCookieIsMissing() {
        // Given — signed in, but no onboarding_session cookie

        // When
        var response = mockMvc.post()
                .uri("/api/v1/connections/reconfigure")
                .header("Origin", ORIGIN)
                .cookie(new MockCookie("bm_session", TEST_API_KEY))
                .exchange();

        // Then
        assertThat(response).hasStatus(401);
    }

    /** /connections/** isn't a public path: the user must be signed in as well as hold the onboarding cookie. */
    private MockMvcTester.MockMvcRequestBuilder authedReconfigure(String sessionToken) {
        return mockMvc.post()
                .uri("/api/v1/connections/reconfigure")
                .header("Origin", ORIGIN)
                .cookie(new MockCookie("bm_session", TEST_API_KEY),
                        new MockCookie("onboarding_session", sessionToken));
    }

    private String createSession(String projectRef, String dbRole, String ownerEmail) {
        return onboardingSessionStore.create(baseSession(projectRef, dbRole, ownerEmail).build());
    }

    private OnboardingSession.OnboardingSessionBuilder baseSession(String projectRef, String dbRole, String ownerEmail) {
        return OnboardingSession.builder()
                .accountId(account.getId())
                .projectRef(projectRef)
                .projectUrl("https://" + projectRef + ".supabase.co")
                .poolerPrefix("aws-0-eu-west-1")
                .dbRole(dbRole)
                .encryptedRolePassword(OLD_ROLE.equals(dbRole) ? "encrypted-creds" : "enc-new-pw")
                .connectionVerified(true)
                .ownerEmail(ownerEmail)
                .encryptedAccessToken(SecurityUtil.encrypt(ACCESS_TOKEN, encryptionProperties.key()))
                .encryptedRefreshToken("enc-new-refresh")
                .tokenExpiresAt(Instant.now().plusSeconds(3600))
                .expiresAt(Instant.now().plusSeconds(1800))
                .schemaDetails(new OnboardingSession.SchemaDetails("subscriptions", "public", "user_id", true))
                .detectedColumns(List.of(new DetectedColumn("status", "text", true, 2, false)))
                .authColumns(List.of())
                .confirmedColumnNames(List.of("status"));
    }
}
