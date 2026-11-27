package com.broadcastmail.api.security;

import com.broadcastmail.api.IntegrationTest;
import com.broadcastmail.api.TestContainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/** With ORIGIN_SECRET set, only requests that came through Cloudflare (which adds the header) reach the API. */
@SpringBootTest(properties = "app.origin-secret=" + OriginSecretTest.SECRET)
@AutoConfigureMockMvc
@Import(TestContainersConfiguration.class)
class OriginSecretTest extends IntegrationTest {

    static final String SECRET = "test-origin-secret";

    // Not mapped and needs a session: a 401 proves the request got past the origin check.
    private static final String PROTECTED = "/api/v1/anything";

    @Autowired
    private MockMvcTester mockMvc;

    @Test
    void shouldRefuseRequestsThatBypassedCloudflare() {
        // When
        var missing = mockMvc.get().uri(PROTECTED);
        var wrong = mockMvc.get().uri(PROTECTED).header(OriginSecretFilter.HEADER, "guess");

        // Then
        assertThat(missing).hasStatus(403);
        assertThat(wrong).hasStatus(403);
    }

    @Test
    void shouldLetRequestsFromCloudflareThrough() {
        // When
        var response = mockMvc.get().uri(PROTECTED).header(OriginSecretFilter.HEADER, SECRET);

        // Then
        assertThat(response).hasStatus(401);
    }

    @Test
    void shouldLetRailwaysHealthCheckThroughWithoutTheSecret() {
        // When
        var response = mockMvc.get().uri("/actuator/health").exchange();

        // Then
        assertThat(response.getResponse().getStatus()).isNotEqualTo(403);
    }
}
