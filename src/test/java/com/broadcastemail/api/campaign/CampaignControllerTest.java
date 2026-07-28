package com.broadcastemail.api.campaign;

import com.broadcastemail.api.TestContainersConfiguration;
import com.broadcastemail.api.account.Account;
import com.broadcastemail.api.account.AccountRepository;
import com.broadcastemail.api.campaign.dto.CreateCampaignRequest;
import com.broadcastemail.api.connection.Connection;
import com.broadcastemail.api.connection.ConnectionRepository;
import com.broadcastemail.api.support.CampaignTestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static com.broadcastemail.api.support.CampaignTestFixtures.TEST_API_KEY;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestContainersConfiguration.class)
class CampaignControllerTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CampaignRepository campaignRepository;
    @Autowired
    private ConnectionRepository connectionRepository;

    private Account account;
    private Connection connection;



    @BeforeEach
    void setUp() {
        account = accountRepository.save(CampaignTestFixtures.account().build());
        connection = connectionRepository.save(CampaignTestFixtures.connection(account.getId()).build());
    }

    @AfterEach
    void tearDown() {
        connectionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void shouldReturn201WithCampaignOnCreate() throws Exception {
        // Given
        CreateCampaignRequest request = new CreateCampaignRequest("Newsletter", "Hello", "<p>Hi</p>", null, connection.getId(),null);
        String requestBody = objectMapper.writeValueAsString(request);

        // When
        var response = authedPost("/api/v1/campaigns")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .exchange();

        // Then
        assertThat(response).hasStatus(201);
    }

    @Test
    void shouldReturn404WhenCampaignDoesNotExist() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When
        var response = authedGet("/api/v1/campaigns/" + nonExistentId)
                .exchange();

        // Then
        assertThat(response).hasStatus(404);
    }

    @Test
    void shouldReturn204OnDelete() {
        // Given
        Campaign campaign = campaignRepository.save(
                CampaignTestFixtures.draftCampaign(account.getId(), connection.getId()).build());

        // When
        var response = authedDelete("/api/v1/campaigns/" + campaign.getId())
                .exchange();

        // Then
        assertThat(response).hasStatus(204);
    }

    @Test
    void shouldReturn401WhenNotAuthenticated() {
        // When
        var response = mockMvc.get()
                .uri("/api/v1/campaigns")
                .exchange();

        // Then
        assertThat(response).hasStatus(401);
    }

    // Helpers
    private MockMvcTester.MockMvcRequestBuilder authedGet(String uri) {
        return mockMvc.get().uri(uri).header("X-API-Key", TEST_API_KEY);
    }

    private MockMvcTester.MockMvcRequestBuilder authedPost(String uri) {
        return mockMvc.post().uri(uri).header("X-API-Key", TEST_API_KEY);
    }

    private MockMvcTester.MockMvcRequestBuilder authedDelete(String uri) {
        return mockMvc.delete().uri(uri).header("X-API-Key", TEST_API_KEY);
    }
}