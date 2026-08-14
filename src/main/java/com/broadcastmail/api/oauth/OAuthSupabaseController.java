package com.broadcastmail.api.oauth;

import com.broadcastmail.api.auth.CookieService;
import com.broadcastmail.api.oauth.dto.OAuthCallbackResult;
import com.broadcastmail.api.oauth.dto.SelectProjectRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/oauth/supabase")
@RequiredArgsConstructor
public class OAuthSupabaseController {

    private final OAuthSupabaseService oAuthSupabaseService;
    private final CookieService cookieService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize() {
        String url = oAuthSupabaseService.buildAuthorizationUrl();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, url)
                .build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam String code,
            @RequestParam String state,
            HttpServletResponse response) {

        OAuthCallbackResult result = oAuthSupabaseService.handleCallback(code, state);

        return switch (result) {
            case OAuthCallbackResult.ReturningUser(String apiKey) -> {
                response.addHeader(HttpHeaders.SET_COOKIE,
                        cookieService.createSessionCookie(apiKey).toString());
                yield ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, frontendUrl + "/dashboard")
                        .build();
            }
            case OAuthCallbackResult.NewUserMultipleProjects(var projects, String partialSessionToken) ->
                    ResponseEntity.status(HttpStatus.FOUND)
                            .header(HttpHeaders.LOCATION,
                                    frontendUrl + "/onboarding/select-project?partialToken="
                                            + partialSessionToken)
                            .build();
            case OAuthCallbackResult.NewUserSingleProject(String sessionToken) -> {
                response.addHeader(HttpHeaders.SET_COOKIE,
                        cookieService.createOnboardingCookie(sessionToken).toString());
                yield ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, frontendUrl + "/onboarding/email-provider")
                        .build();
            }
        };
    }

    @PostMapping("/select-project")
    public ResponseEntity<Void> selectProject(
            @RequestBody SelectProjectRequest request,
            HttpServletResponse response) {

        OAuthCallbackResult.NewUserSingleProject result =
                oAuthSupabaseService.selectProject(
                        request.projectRef(),
                        request.partialSessionToken()
                );

        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.createOnboardingCookie(result.sessionToken()).toString());

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, frontendUrl + "/onboarding/email-provider")
                .build();
    }

}