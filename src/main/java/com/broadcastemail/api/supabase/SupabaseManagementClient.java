package com.broadcastemail.api.supabase;

import com.broadcastemail.api.supabase.dto.SupabaseProject;
import com.broadcastemail.api.supabase.dto.SupabaseTokenResponse;
import com.broadcastemail.api.supabase.dto.SupabaseUser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.misc.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SupabaseManagementClient {
    private final RestClient supabaseRestClient;
    private final Pair<String, String> authorizationHeader = new Pair<>("Authorization", "Bearer ");
    @Value("${supabase.oauth.client-id}")
    private String clientId;

    @Value("${supabase.oauth.client-secret}")
    private String clientSecret;

    @Value("${supabase.oauth.redirect-uri}")
    private String redirectUri;

    public SupabaseTokenResponse exchangeCodeForTokens(String code){
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("code", code);
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("redirect_uri", redirectUri);

        return supabaseRestClient.post()
                .uri("/api/v1/oauth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(formData)
                .retrieve()
                .body(SupabaseTokenResponse.class);
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
                .uri("/api/v1/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(SupabaseTokenResponse.class);
    }

    public List<SupabaseProject> listProjects(String accessToken) {
        return supabaseRestClient.get()
                .uri("api/v1/projects")
                .header(authorizationHeader.a, authorizationHeader.b + accessToken)
                .retrieve()
                .body(new ParameterizedTypeReference<List<SupabaseProject>>(){});
    }
    public void executeSql(String accessToken, String projectRef, String sql){
        supabaseRestClient.post()
                .uri("/v1/projects/{ref}/database/query", projectRef)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(Map.of("query", sql))
                .retrieve()
                .toBodilessEntity();
    }

    public String getOwnerEmail(String accessToken) {
        SupabaseUser user = supabaseRestClient.get()
                .uri("/v1/user")
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
