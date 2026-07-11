package com.broadcastemail.api.auth;

import com.broadcastemail.api.TestContainersConfiguration;
import com.broadcastemail.api.account.AccountRepository;
import com.broadcastemail.api.auth.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestContainersConfiguration.class)

class AuthControllerTest {
    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
    }

    @Test
    void shouldCreateAccountAndReturnApiKey()  throws Exception
    {
        // Given
        RegisterRequest request = new RegisterRequest(
                "test@broadcastmail.io",
                "password123"
        );

        // When / Then
        assertThat(mockMvc.post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .hasStatus(201)
                .bodyJson()
                .extractingPath("$.apiKey")
                .asString()
                .startsWith("bm_live_");
    }

    @Test
    void shouldRejectInvalidEmailFormat() {
        // Given
        RegisterRequest request = new RegisterRequest(
                "test",
                "password123"
        );

        // When / Then
        assertThat(mockMvc.post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .hasStatus(400);

    }

    @Test
    void shouldRejectPasswordUnderMinimumLength()
    {
        // Given
        RegisterRequest request = new RegisterRequest(
                "test@broadcastmail.io",
                "test"
        );

        // When / Then
        assertThat(mockMvc.post().uri("/api/v1/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .hasStatus(400);
    }

    @Test
    void shouldRejectDuplicateEmail()
    {
        RegisterRequest request = new RegisterRequest(
                "test@broadcastmail.io",
                "password123"
        );

        // When
        assertThat(mockMvc.post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .hasStatus(201);

        // Then
        assertThat(mockMvc.post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .hasStatus(400);
    }

    @Test
    void shouldGenerateUniqueApiKeyPerAccount() throws Exception {

        // Given
        RegisterRequest first = new RegisterRequest(
                "first@broadcastmail.io",
                "password123"
        );
        RegisterRequest second = new RegisterRequest(
                "second@broadcastmail.io",
                "password123"
        );

        // When
        MvcTestResult firstResult = mockMvc.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(first))
                .exchange();

        MvcTestResult secondResult = mockMvc.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(second))
                .exchange();

        String firstKey = objectMapper.readTree(
                firstResult.getResponse().getContentAsString()
        ).get("apiKey").asText();

        String secondKey = objectMapper.readTree(
                secondResult.getResponse().getContentAsString()
        ).path("apiKey").textValue();

        // Then
        assertThat(firstKey).isNotEqualTo(secondKey);
    }
}