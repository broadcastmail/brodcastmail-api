package com.broadcastmail.api.connection;

import com.broadcastmail.api.TestContainersConfiguration;
import com.broadcastmail.api.TestSecurityConfig;
import com.broadcastmail.api.connection.dto.ConnectionRequests;
import com.broadcastmail.api.connection.dto.DetectedColumn;
import com.broadcastmail.api.connection.dto.SchemaIntrospectionResult;
import com.broadcastmail.api.filterablecolumn.FilterableColumn;
import com.broadcastmail.api.filterablecolumn.FilterableColumnRepository;
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
        var response = authedPatch("/api/v1/connections/project").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ConnectionRequests.UpdateProjectRequest("new-ref"))).exchange();

        assertThat(response).hasStatus(200);
    }

    @Test
    void shouldNullDownstreamFieldsWhenUpdatingProject() throws Exception {
        var response = authedPatch("/api/v1/connections/project").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ConnectionRequests.UpdateProjectRequest("new-ref"))).exchange();

        assertThat(response).hasStatus(200);

        Connection updated = connectionRepository.findByAccountId(account.getId()).orElseThrow();

        assertThat(updated.getProjectRef()).isEqualTo("new-ref");
        assertThat(updated.getUserTableName()).isNull();
        assertThat(updated.getUserTableSchema()).isNull();
        assertThat(updated.getEmailColumn()).isNull();
    }

    @Test
    void shouldReturn200WhenUpdatingTable() throws Exception {
        var response = authedPatch("/api/v1/connections/table").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ConnectionRequests.UpdateTableRequest("public", "profiles"))).exchange();

        assertThat(response).hasStatus(200);
    }

    @Test
    void shouldNullEmailColumnWhenUpdatingTable() throws Exception {
        authedPatch("/api/v1/connections/table").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ConnectionRequests.UpdateTableRequest("public", "profiles"))).exchange();

        Connection updated = connectionRepository.findByAccountId(account.getId()).orElseThrow();
        assertThat(updated.getUserTableName()).isEqualTo("profiles");
        assertThat(updated.getEmailColumn()).isNull();
    }

    @Test
    void shouldReturn200WhenUpdatingEmailColumn() throws Exception {
        var response = authedPatch("/api/v1/connections/email-column").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ConnectionRequests.UpdateEmailColumnRequest("email"))).exchange();

        assertThat(response).hasStatus(200);
    }

    @Test
    void shouldReturn200WhenUpdatingColumns() throws Exception {
        when(schemaIntrospectionService.introspect(any(), any())).thenReturn(
                new SchemaIntrospectionResult.Detected("profiles", "public", "email", "id",
                        List.of(new DetectedColumn("plan", "text", true, null, false),
                                new DetectedColumn("is_verified", "boolean", true, null, false))));

        var response = authedPatch("/api/v1/connections/columns").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ConnectionRequests.UpdateColumnsRequest(List.of("plan", "is_verified")))).exchange();

        assertThat(response).hasStatus(200);
    }

    @Test
    void shouldDeleteExistingColumnsWhenUpdatingColumns() throws Exception {
        when(schemaIntrospectionService.introspect(any(), any())).thenReturn(
                new SchemaIntrospectionResult.Detected("profiles", "public", "email", "id",
                        List.of(new DetectedColumn("plan", "text", true, null, false),
                                new DetectedColumn("is_verified", "boolean", true, null, false))));

        filterableColumnRepository.save(
                FilterableColumn.builder().connectionId(connection.getId()).columnName("old_column").columnType("text").displayName("old_column")
                        .enabled(true).cardinalityWarning(false).build());

        authedPatch("/api/v1/connections/columns").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ConnectionRequests.UpdateColumnsRequest(List.of("plan", "is_verified")))).exchange();

        assertThat(filterableColumnRepository.findByConnectionId(connection.getId())).extracting(FilterableColumn::getColumnName)
                .containsExactlyInAnyOrder("plan", "is_verified");
    }

    @Test
    void shouldReturn401WhenNotAuthenticated() throws Exception {
        var response = mockMvc.patch().uri("/api/v1/connections/project").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ConnectionRequests.UpdateProjectRequest("new-ref"))).exchange();

        assertThat(response).hasStatus(401);
    }

    private MockMvcTester.MockMvcRequestBuilder authedPatch(String uri) {
        return mockMvc.patch().uri(uri).cookie(new MockCookie("bm_session", TEST_API_KEY));
    }
}