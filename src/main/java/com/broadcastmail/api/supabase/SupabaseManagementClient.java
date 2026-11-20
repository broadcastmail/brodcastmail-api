package com.broadcastmail.api.supabase;

import com.broadcastmail.api.supabase.dto.SupabaseProject;
import com.broadcastmail.api.supabase.dto.SupabaseTokenResponse;
import com.broadcastmail.api.supabase.dto.SupabaseUser;
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

    public SupabaseTokenResponse exchangeCodeForTokensPKCE(String code, String codeVerifier) {
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

    public List<SupabaseProject> listProjects(String accessToken) {
        return supabaseRestClient.get()
                .uri("/v1/projects")
                .header(authorizationHeader.a, authorizationHeader.b + accessToken)
                .retrieve()
                .body(new ParameterizedTypeReference<List<SupabaseProject>>(){});
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

    public String getOwnerEmail(String accessToken) {
        SupabaseUser user = supabaseRestClient.get()
                .uri("/v1/profile")
                .header(authorizationHeader.a, authorizationHeader.b + accessToken)
                .retrieve()
                .body(SupabaseUser.class);

        if (user == null || user.email() == null) {
            throw new IllegalStateException(
                    "Could not retrieve email from Supabase account"
            );
        }

        return user.email();
    }
}
