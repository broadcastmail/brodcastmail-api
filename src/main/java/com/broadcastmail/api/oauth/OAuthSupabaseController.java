package com.broadcastmail.api.oauth;

import com.broadcastmail.api.auth.CookieService;
import com.broadcastmail.api.oauth.dto.OAuthCallbackResult;
import com.broadcastmail.api.oauth.dto.ProjectOption;
import com.broadcastmail.api.oauth.dto.SelectProjectRequest;
import com.broadcastmail.api.onboarding.PartialOnboardingSession;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/v1/oauth/supabase")
@RequiredArgsConstructor
public class OAuthSupabaseController {

    private final OAuthSupabaseService oAuthSupabaseService;
    private final CookieService cookieService;
    private final OAuthSessionService oAuthSessionService;
    private final OAuthSessionStore oAuthSessionStore;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(
            @RequestParam(required = false) String intent,
            @CookieValue(name="bm_session", required = false) String sessionCookie
    ) {
        if("reconfigure".equals(intent) && sessionCookie != null) {
            String url = oAuthSupabaseService.buildReconfigureAuthorizationUrl(sessionCookie);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, url)
                    .build();
        }
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
            case OAuthCallbackResult.NewUserMultipleProjects(String partialSessionToken) ->
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
            case OAuthCallbackResult.ReconfigureSingleProject(String sessionToken) -> {
                response.addHeader(HttpHeaders.SET_COOKIE,
                        cookieService.createOnboardingCookie(sessionToken).toString());
                yield ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, frontendUrl + "/settings/reconfigure/schema")
                        .build();
            }
            case  OAuthCallbackResult.ReconfigureMultipleProjects(String partialSessionToken) ->
                ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION,
                                frontendUrl + "/settings/reconfigure/select-project?partialToken="
                                        + partialSessionToken)
                        .build();
        };
    }

    @GetMapping("/projects")
    public ResponseEntity<List<ProjectOption>> listProjects(@RequestParam String partialToken) {
        return ResponseEntity.ok(oAuthSessionService.listPartialProjects(partialToken));
    }

    @PostMapping("/select-project")
    public ResponseEntity<Void> selectProject(
            @RequestBody SelectProjectRequest request,
            HttpServletResponse response) {

        PartialOnboardingSession partial = oAuthSessionStore
                .getPartial(request.partialSessionToken());
        boolean isReconfigure = partial.isReconfigure();

        OAuthCallbackResult.NewUserSingleProject result = oAuthSessionService.selectProject(
                request.projectRef(),
                request.partialSessionToken()
        );

        String redirectUrl = isReconfigure
                ? frontendUrl + "/settings/reconfigure/schema"
                : frontendUrl + "/onboarding/email-provider";

        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.createOnboardingCookie(result.sessionToken()).toString());

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirectUrl)
                .build();
    }



}