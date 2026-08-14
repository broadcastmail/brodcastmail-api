package com.broadcastmail.api.config;

import com.broadcastmail.api.TestContainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestContainersConfiguration.class)
class CorsConfigTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Test
    void shouldAllowConfiguredOriginOnPreflightRequest() {
        // Given — http://localhost:3000 is always allowed by CorsConfig

        // When
        var response = mockMvc.method(HttpMethod.OPTIONS)
                .uri("/api/v1/onboarding/status")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET")
                .exchange();

        // Then
        assertThat(response).hasStatus(200);
        assertThat(response.getResponse().getHeader("Access-Control-Allow-Origin"))
                .isEqualTo("http://localhost:3000");
        assertThat(response.getResponse().getHeader("Access-Control-Allow-Credentials"))
                .isEqualTo("true");
    }

    @Test
    void shouldRejectDisallowedOriginOnPreflightRequest() {
        // Given

        // When
        var response = mockMvc.method(HttpMethod.OPTIONS)
                .uri("/api/v1/onboarding/status")
                .header("Origin", "http://evil.example.com")
                .header("Access-Control-Request-Method", "GET")
                .exchange();

        // Then
        assertThat(response).hasStatus(403);
    }
}
