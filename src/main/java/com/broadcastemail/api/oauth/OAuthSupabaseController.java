package com.broadcastemail.api.oauth;

import com.broadcastemail.api.oauth.dto.OAuthCallbackResult;
import com.broadcastemail.api.oauth.dto.SelectProjectRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/oauth/supabase")
@RequiredArgsConstructor
public class OAuthSupabaseController {

    private final OAuthSupabaseService oAuthSupabaseService;

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

        if (result.requiresProjectSelection()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION,
                            frontendUrl + "/onboarding/select-project?partialToken="
                                    + result.partialSessionToken())
                    .build();
        }

        setSessionCookie(response, result.sessionToken());

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, frontendUrl + "/onboarding/email-provider")
                .build();
    }

    @PostMapping("/select-project")
    public ResponseEntity<Void> selectProject(
            @RequestBody SelectProjectRequest request,
            HttpServletResponse response) {

        OAuthCallbackResult result = oAuthSupabaseService.selectProject(
                request.projectRef(),
                request.partialSessionToken()
        );

        setSessionCookie(response, result.sessionToken());

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, frontendUrl + "/onboarding/email-provider")
                .build();
    }

    private void setSessionCookie(HttpServletResponse response, String sessionToken) {
        ResponseCookie cookie = ResponseCookie.from("onboarding_session", sessionToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .maxAge(Duration.ofMinutes(30))
                .path("/")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}