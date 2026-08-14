package com.broadcastmail.api.auth;

import com.broadcastmail.api.TestContainersConfiguration;
import com.broadcastmail.api.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestContainersConfiguration.class, TestSecurityConfig.class})
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Test
    void shouldReturn204OnLogout() {
        // When
        var response = mockMvc.post()
                .uri("/api/v1/auth/logout")
                .exchange();

        // Then
        assertThat(response).hasStatus(204);
    }

    @Test
    void shouldClearSessionCookieOnLogout() {
        // When
        var response = mockMvc.post()
                .uri("/api/v1/auth/logout")
                .exchange();

        // Then
        assertThat(response.getResponse().getHeader("Set-Cookie"))
                .contains("bm_session=")
                .contains("Max-Age=0");
    }
}
