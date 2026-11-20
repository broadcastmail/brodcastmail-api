package com.broadcastmail.api.supabase;

import com.broadcastmail.api.supabase.dto.SupabaseProject;
import com.broadcastmail.api.supabase.dto.SupabaseTokenResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.misc.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SupabaseManagementClient {
    private static final Logger log = LoggerFactory.getLogger(SupabaseManagementClient.class);
    private final RestClient supabaseRestClient;
    private final Pair<String, String> authorizationHeader = new Pair<>("Authorization", "Bearer ");
    @Value("${supabase.oauth.client-id}")
    private String clientId;

    @Value("${supabase.oauth.client-secret}")
    private String clientSecret;

    @Value("${supabase.oauth.redirect-uri}")
    private String redirectUri;

    public SupabaseTokenResponse exchangeCodeForTokensPKCE(String code, String codeVerifier, String redirectUri) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("code", code);
        formData.add("code_verifier", codeVerifier);
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("redirect_uri", redirectUri);

        return supabaseRestClient.post()
                .uri("/v1/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .exchange((req, res) -> {
                    if (!res.getStatusCode().is2xxSuccessful() ||
                            !org.springframework.http.MediaType.APPLICATION_JSON.isCompatibleWith(res.getHeaders().getContentType())) {
                        String body = new String(res.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        log.error("Supabase token exchange failed [{}] [{}]: {}", res.getStatusCode(), res.getHeaders().getContentType(), body);
                        throw new IllegalStateException("Supabase token exchange failed");
                    }
                    return res.bodyTo(SupabaseTokenResponse.class);
                });
    }

    public SupabaseTokenResponse refreshAccessToken(String refreshToken)
    {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("refresh_token", refreshToken);
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("redirect_uri", redirectUri);

        return supabaseRestClient.post()
                .uri("/v1/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(SupabaseTokenResponse.class);
    }

    public String getPoolerPrefix(String accessToken, String projectRef) {
        try {
            String json = supabaseRestClient.get()
                    .uri("/v1/projects/{ref}", projectRef)
                    .header(authorizationHeader.a, authorizationHeader.b + accessToken)
                    .retrieve()
                    .body(String.class);
            var tree = OBJECT_MAPPER.readTree(json);
            String provider = tree.path("cloud_provider").asText("aws").toLowerCase();
            String region = tree.path("region").asText();
            return provider + "-0-" + region;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to get pooler prefix for " + projectRef, e);
        }
    }

    public List<SupabaseProject> listProjects(String accessToken) {
        return supabaseRestClient.get()
                .uri("/v1/projects")
                .header(authorizationHeader.a, authorizationHeader.b + accessToken)
                .retrieve()
                .body(new ParameterizedTypeReference<List<SupabaseProject>>(){});
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public String resolveOwnerEmail(String accessToken, String projectRef) {
        try {
            String projectJson = supabaseRestClient.get()
                    .uri("/v1/projects/{ref}", projectRef)
                    .header(authorizationHeader.a, authorizationHeader.b + accessToken)
                    .retrieve()
                    .body(String.class);

            JsonNode project = OBJECT_MAPPER.readTree(projectJson);
            String orgSlug = project.path("organization_slug").asText(null);
            if (orgSlug == null) {
                log.warn("resolveOwnerEmail: no organization_slug for project {}", projectRef);
                return null;
            }

            String membersJson = supabaseRestClient.get()
                    .uri("/v2/organizations/{slug}/members", orgSlug)
                    .header(authorizationHeader.a, authorizationHeader.b + accessToken)
                    .retrieve()
                    .body(String.class);

            JsonNode data = OBJECT_MAPPER.readTree(membersJson).path("data");
            if (!data.isArray() || data.isEmpty()) {
                log.warn("resolveOwnerEmail: no members found for org {}", orgSlug);
                return null;
            }

            String ownerEmail = findOwnerEmail(data);
            if (ownerEmail == null) {
                log.warn("resolveOwnerEmail: no owner role found for org {}, using first member email", orgSlug);
            }
            return ownerEmail;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to resolve owner email", e);
        }
    }

    private String findOwnerEmail(JsonNode data) {
        for (JsonNode member : data) {
            JsonNode attrs = member.path("attributes");
            for (JsonNode role : attrs.path("roles")) {
                if ("Owner".equals(role.path("name").asText()) && "organization".equals(role.path("scope").asText())) {
                    String email = attrs.path("primary_email").asText(null);
                    if (email != null && !email.isBlank()) return email;
                }
            }
        }
        return data.get(0).path("attributes").path("primary_email").asText(null);
    }

    public void executeSql(String accessToken, String projectRef, String sql){
        executeSqlQuery(accessToken, projectRef, sql);
    }

    public List<Map<String, Object>> executeSqlQuery(String accessToken, String projectRef, String sql) {
        return supabaseRestClient.post()
                .uri("/v1/projects/{ref}/database/query", projectRef)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(Map.of("query", sql))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    }

}
