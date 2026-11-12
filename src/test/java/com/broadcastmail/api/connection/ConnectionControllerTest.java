package com.broadcastmail.api.connection;

import com.broadcastmail.api.TestContainersConfiguration;
import com.broadcastmail.api.TestSecurityConfig;
import com.broadcastmail.api.connection.dto.ConnectionRequests;
import com.broadcastmail.api.connection.dto.DetectedColumn;
import com.broadcastmail.api.connection.dto.SchemaIntrospectionResult;
import com.broadcastmail.api.emailprovider.EmailProviderService;
import com.broadcastmail.api.emailprovider.dto.EmailProviderInfoResponse;
import com.broadcastmail.api.emailprovider.dto.VerifyKeyResponse;
import com.broadcastmail.api.supabase.TenantConnectionGateway;
import com.broadcastmail.common.campaign.filter.FilterSource;
import com.broadcastmail.common.filterablecolumn.FilterableColumn;
import com.broadcastmail.common.filterablecolumn.FilterableColumnRepository;
import com.broadcastmail.api.support.CampaignTestFixtures;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static com.broadcastmail.api.support.CampaignTestFixtures.TEST_API_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestContainersConfiguration.class, TestSecurityConfig.class})
class ConnectionControllerTest {


    @Autowired
    private MockMvcTester mockMvc;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private ConnectionRepository connectionRepository;
    @Autowired
    private FilterableColumnRepository filterableColumnRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private SchemaIntrospectionService schemaIntrospectionService;
    @MockitoBean
    private EmailProviderService emailProviderService;
    @MockitoBean
    private TenantConnectionGateway tenantConnectionGateway;

    private Account account;
    private Connection connection;

    @BeforeEach
    void setUp() {
        account = accountRepository.saveAndFlush(CampaignTestFixtures.account().build());

        connection = connectionRepository.saveAndFlush(CampaignTestFixtures.connection(account.getId()).build());
    }

    @AfterEach
    void tearDown() {
        filterableColumnRepository.deleteAll();
        connectionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void shouldReturn200WhenUpdatingProject() throws Exception {
        var response = authedPatch("/api/v1/connections/reconnect/project").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ConnectionRequests.UpdateProjectRequest("new-ref"))).exchange();

        assertThat(response).hasStatus(200);
    }

    @Test
    void shouldNullDownstreamFieldsWhenUpdatingProject() throws Exception {
        authedPatch("/api/v1/connections/reconnect/project").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ConnectionRequests.UpdateProjectRequest("new-ref"))).exchange();

        Connection updated = connectionRepository.findByAccountId(account.getId()).orElseThrow();

        assertThat(updated.getProjectRef()).isEqualTo("new-ref");
        assertThat(updated.getUserTableName()).isNull();
        assertThat(updated.getUserTableSchema()).isNull();
        assertThat(updated.getEmailColumn()).isNull();
    }

    @Test
    void shouldReturn200WhenUpdatingUserSchema() throws Exception {
        var response = authedPatch("/api/v1/connections/reconnect/user-schema").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ConnectionRequests.UpdateTableRequest("public", "profiles", "id"))).exchange();

        assertThat(response).hasStatus(200);
    }

    @Test
    void shouldPersistUserSchemaWhenUpdatingUserSchema() throws Exception {
        authedPatch("/api/v1/connections/reconnect/user-schema").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ConnectionRequests.UpdateTableRequest("public", "profiles", "id"))).exchange();

        Connection updated = connectionRepository.findByAccountId(account.getId()).orElseThrow();
        assertThat(updated.getUserTableName()).isEqualTo("profiles");
        assertThat(updated.getUserIdColumn()).isEqualTo("id");
        assertThat(updated.getEmailColumn()).isEqualTo(connection.getEmailColumn());
    }

    @Test
    void shouldReturn200WhenUpdatingColumns() throws Exception {
        when(schemaIntrospectionService.introspectTable(any(), any(), any(), any(), any())).thenReturn(
                new SchemaIntrospectionResult.Detected("profiles", "public", "id",
                        List.of(new DetectedColumn("plan", "text", true, null, false),
                                new DetectedColumn("is_verified", "boolean", true, null, false)),
                        List.of()));

        var response = authedPatch("/api/v1/connections/reconnect/columns").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ConnectionRequests.UpdateColumnsRequest(List.of("plan", "is_verified")))).exchange();

        assertThat(response).hasStatus(200);
    }

    @Test
    void shouldDeleteExistingColumnsWhenUpdatingColumns() throws Exception {
        when(schemaIntrospectionService.introspectTable(any(), any(), any(), any(), any())).thenReturn(
                new SchemaIntrospectionResult.Detected("profiles", "public", "id",
                        List.of(new DetectedColumn("plan", "text", true, null, false),
                                new DetectedColumn("is_verified", "boolean", true, null, false)),
                        List.of()));

        filterableColumnRepository.save(
                FilterableColumn.builder().connectionId(connection.getId()).columnName("old_column").columnType("text").displayName("old_column")
                        .source(FilterSource.PROFILE_TABLE).enabled(true).cardinalityWarning(false).build());

        authedPatch("/api/v1/connections/reconnect/columns").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ConnectionRequests.UpdateColumnsRequest(List.of("plan", "is_verified")))).exchange();

        assertThat(filterableColumnRepository.findByConnectionId(connection.getId())).extracting(FilterableColumn::getColumnName)
                .containsExactlyInAnyOrder("plan", "is_verified");
    }

    @Test
    void shouldReturn401WhenNotAuthenticated() throws Exception {
        var response = mockMvc.patch().uri("/api/v1/connections/reconnect/project").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ConnectionRequests.UpdateProjectRequest("new-ref"))).exchange();

        assertThat(response).hasStatus(401);
    }

    // ── DELETE /reconnect/profile-columns ─────────────────────────────────

    @Test
    void shouldReturn204WhenDeletingProfileColumns()  {
        filterableColumnRepository.save(
                FilterableColumn.builder().connectionId(connection.getId()).columnName("plan").columnType("text").displayName("plan")
                        .source(FilterSource.PROFILE_TABLE).enabled(true).cardinalityWarning(false).build());

        var response = authedDelete("/api/v1/connections/reconnect/profile-columns").exchange();

        assertThat(response).hasStatus(204);
    }

    @Test
    void shouldRemoveProfileTableColumnsWhenDeletingProfileColumns()  {
        filterableColumnRepository.save(
                FilterableColumn.builder().connectionId(connection.getId()).columnName("plan").columnType("text").displayName("plan")
                        .source(FilterSource.PROFILE_TABLE).enabled(true).cardinalityWarning(false).build());
        filterableColumnRepository.save(
                FilterableColumn.builder().connectionId(connection.getId()).columnName("email").columnType("text").displayName("email")
                        .source(FilterSource.AUTH_METADATA).enabled(true).cardinalityWarning(false).build());

        authedDelete("/api/v1/connections/reconnect/profile-columns").exchange();

        assertThat(filterableColumnRepository.findByConnectionId(connection.getId()))
                .extracting(FilterableColumn::getSource)
                .doesNotContain(FilterSource.PROFILE_TABLE);
    }

    // ── GET /columns ───────────────────────────────────────────────────────

    @Test
    void shouldReturnColumnsForAccount()  {
        filterableColumnRepository.save(
                FilterableColumn.builder().connectionId(connection.getId()).columnName("plan").columnType("text").displayName("plan")
                        .source(FilterSource.PROFILE_TABLE).enabled(true).cardinalityWarning(false).build());

        var response = authedGet("/api/v1/connections/columns").exchange();

        assertThat(response).hasStatus(200)
                .bodyJson().extractingPath("$[0].columnName").asString().isEqualTo("plan");
    }

    @Test
    void shouldReturnEmptyListWhenNoColumns()  {
        var response = authedGet("/api/v1/connections/columns").exchange();

        assertThat(response).hasStatus(200)
                .bodyJson().extractingPath("$").asList().isEmpty();
    }

    // ── GET /filters ──────────────────────────────────────────────────────

    @Test
    void shouldReturnFilterContextWithConnectionInfo() {
        // When
        var response = authedGet("/api/v1/connections/filters").exchange();

        // Then
        assertThat(response).hasStatus(200)
                .bodyJson().extractingPath("$.userTable.name").asString().isEqualTo("profiles");
        assertThat(response).bodyJson().extractingPath("$.userTable.schema").asString().isEqualTo("public");
        assertThat(response).bodyJson().extractingPath("$.status").asString().isEqualTo("DETECTED");
    }

    @Test
    void shouldSplitColumnsIntoProfileAndAuth() {
        // Given
        filterableColumnRepository.save(FilterableColumn.builder()
                .connectionId(connection.getId()).columnName("plan").columnType("text")
                .displayName("plan").source(FilterSource.PROFILE_TABLE).enabled(true).cardinalityWarning(false).build());
        filterableColumnRepository.save(FilterableColumn.builder()
                .connectionId(connection.getId()).columnName("created_at").columnType("timestamptz")
                .displayName("created_at").source(FilterSource.AUTH_METADATA).enabled(true).cardinalityWarning(false).build());

        // When
        var response = authedGet("/api/v1/connections/filters").exchange();

        // Then
        assertThat(response).hasStatus(200)
                .bodyJson().extractingPath("$.columns.profile[0].name").asString().isEqualTo("plan");
        assertThat(response).bodyJson().extractingPath("$.columns.auth[0].name").asString().isEqualTo("created_at");
    }

    @Test
    void shouldIncludeRecommendationMetadataForEachColumn() {
        // Given
        filterableColumnRepository.save(FilterableColumn.builder()
                .connectionId(connection.getId()).columnName("plan").columnType("text")
                .displayName("plan").source(FilterSource.PROFILE_TABLE).enabled(true).cardinalityWarning(false).build());

        // When
        var response = authedGet("/api/v1/connections/filters").exchange();

        // Then — column carries recommendation fields; the logic itself is covered by ColumnRecommendationServiceTest
        assertThat(response).bodyJson().extractingPath("$.columns.profile[0].recommended").asBoolean().isNotNull();
        assertThat(response).bodyJson().extractingPath("$.columns.profile[0].allowedOperators").asList().isNotEmpty();
    }

    @Test
    void shouldReturn401WhenNotAuthenticatedForFilters() {
        // When
        var response = mockMvc.get().uri("/api/v1/connections/filters").exchange();

        // Then
        assertThat(response).hasStatus(401);
    }

    @Test
    void shouldReturnEmptyColumnsWhenNoneConfigured() {
        // When
        var response = authedGet("/api/v1/connections/filters").exchange();

        // Then
        assertThat(response).hasStatus(200)
                .bodyJson().extractingPath("$.columns.profile").asList().isEmpty();
        assertThat(response).bodyJson().extractingPath("$.columns.auth").asList().isEmpty();
    }

    // ── Email provider endpoints ───────────────────────────────────────────

    @Test
    void shouldReturnEmailProviderInfo()  {
        when(emailProviderService.getEmailProviderInfo(any()))
                .thenReturn(new EmailProviderInfoResponse("sender@example.com", "Sender", "k3y1"));

        var response = authedGet("/api/v1/connections/email-provider").exchange();

        assertThat(response).hasStatus(200)
                .bodyJson().extractingPath("$.fromAddress").asString().isEqualTo("sender@example.com");
    }

    @Test
    void shouldReturnDomainsFromEmailProvider()  {
        when(emailProviderService.listDomains(any()))
                .thenReturn(new VerifyKeyResponse(List.of("example.com")));

        var response = authedGet("/api/v1/connections/email-provider/domains").exchange();

        assertThat(response).hasStatus(200)
                .bodyJson().extractingPath("$.domains[0]").asString().isEqualTo("example.com");
    }

    @Test
    void shouldReturn200WhenVerifyingKey()  {
        when(emailProviderService.verifyKey(any()))
                .thenReturn(new VerifyKeyResponse(List.of("example.com")));

        var response = authedPost("/api/v1/connections/email-provider/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"apiKey\":\"re_test_key\"}")
                .exchange();

        assertThat(response).hasStatus(200);
    }

    @Test
    void shouldReturn400WhenVerifyingKeyWithBlankApiKey()  {
        var response = authedPost("/api/v1/connections/email-provider/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"apiKey\":\"\"}")
                .exchange();

        assertThat(response).hasStatus(400);
    }

    @Test
    void shouldReturn200WhenReconfiguringEmailProvider()  {
        var body = "{\"apiKey\":\"re_key\",\"fromName\":\"Sender\",\"fromAddress\":\"sender@example.com\"}";

        var response = mockMvc.put().uri("/api/v1/connections/reconfigure/email-provider")
                .cookie(new MockCookie("bm_session", TEST_API_KEY))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();

        assertThat(response).hasStatus(200);
    }

    @Test
    void shouldReturn200WhenUpdatingEmailProviderSender()  {
        var body = "{\"fromName\":\"New Name\",\"fromAddress\":\"new@example.com\"}";

        var response = authedPatch("/api/v1/connections/reconfigure/email-provider/sender")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();

        assertThat(response).hasStatus(200);
    }

    @Test
    void shouldReturn400WhenUpdatingEmailProviderSenderWithInvalidEmail()  {
        var body = "{\"fromName\":\"Name\",\"fromAddress\":\"not-an-email\"}";

        var response = authedPatch("/api/v1/connections/reconfigure/email-provider/sender")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();

        assertThat(response).hasStatus(400);
    }

    private MockMvcTester.MockMvcRequestBuilder authedPatch(String uri) {
        return mockMvc.patch().uri(uri).cookie(new MockCookie("bm_session", TEST_API_KEY));
    }

    private MockMvcTester.MockMvcRequestBuilder authedGet(String uri) {
        return mockMvc.get().uri(uri).cookie(new MockCookie("bm_session", TEST_API_KEY));
    }

    private MockMvcTester.MockMvcRequestBuilder authedPost(String uri) {
        return mockMvc.post().uri(uri).cookie(new MockCookie("bm_session", TEST_API_KEY));
    }

    private MockMvcTester.MockMvcRequestBuilder authedDelete(String uri) {
        return mockMvc.delete().uri(uri).cookie(new MockCookie("bm_session", TEST_API_KEY));
    }
}