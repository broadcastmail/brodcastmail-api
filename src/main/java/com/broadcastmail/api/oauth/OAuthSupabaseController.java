package com.broadcastmail.api.oauth;

import com.broadcastmail.api.auth.CookieService;
import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.common.exceptions.OAuthStateValidationException;
import com.broadcastmail.api.config.OriginValidator;
import com.broadcastmail.api.oauth.dto.OAuthCallbackResult;
import com.broadcastmail.api.oauth.dto.ProcessCallbackRequest;
import com.broadcastmail.api.oauth.dto.ProjectOption;
import com.broadcastmail.api.oauth.dto.SelectProjectRequest;
import com.broadcastmail.api.onboarding.PartialOnboardingSession;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;


@Tag(name = "oauth")
@RestController
@RequestMapping("/api/v1/oauth/supabase")
@RequiredArgsConstructor
public class OAuthSupabaseController {

    private final OAuthSupabaseService oAuthSupabaseService;
    private final CookieService cookieService;
    private final OAuthSessionService oAuthSessionService;
    private final OAuthSessionStore oAuthSessionStore;
    private final AccountRepository accountRepository;
    private final OriginValidator originValidator;

    // "callback" would generate a useCallback hook that collides with React's
    @Operation(operationId = "oauthCallback")
    @PostMapping("/callback")
    public ResponseEntity<Map<String, String>> callback(
            @RequestBody ProcessCallbackRequest request) {

        if (request.storedState() == null || !request.storedState().equals(request.state())) {
            throw new OAuthStateValidationException();
        }

        originValidator.validateCallbackUri(request.redirectUri());

        int colonIdx = request.state().indexOf(':');
        String intent = colonIdx >= 0 ? request.state().substring(colonIdx + 1) : "new_user";
        boolean isReconfigure = intent.startsWith("reconfigure");

        UUID accountId = null;
        if (isReconfigure && request.bmSession() != null) {
            accountId = accountRepository.findByApiKeyHash(SecurityUtil.sha256(request.bmSession()))
                    .map(Account::getId)
                    .orElse(null);
        }

        OAuthCallbackResult result = oAuthSupabaseService.exchangeWithPKCE(
                request.code(), request.codeVerifier(), request.redirectUri(), accountId);

        return switch (result) {
            case OAuthCallbackResult.ReturningUser(String apiKey) ->
                    ResponseEntity.ok(Map.of("type", "RETURNING_USER", "sessionToken", apiKey));
            case OAuthCallbackResult.NewUserSingleProject(String token) ->
                    ResponseEntity.ok(Map.of("type", "NEW_USER", "onboardingToken", token));
            case OAuthCallbackResult.NewUserMultipleProjects(String partial) ->
                    ResponseEntity.ok(Map.of("type", "MULTIPLE_PROJECTS", "partialToken", partial));
            case OAuthCallbackResult.ReconfigureSingleProject(String token) ->
                    ResponseEntity.ok(Map.of("type", "RECONFIGURE", "onboardingToken", token, "intent", intent));
            case OAuthCallbackResult.ReconfigureMultipleProjects(String partial) ->
                    ResponseEntity.ok(Map.of("type", "RECONFIGURE_MULTIPLE", "partialToken", partial, "intent", intent));
        };
    }

    @GetMapping("/projects")
    public ResponseEntity<List<ProjectOption>> listProjects(@RequestParam String partialToken) {
        return ResponseEntity.ok(oAuthSessionService.listPartialProjects(partialToken));
    }

    @PostMapping("/select-project")
    public ResponseEntity<Map<String, String>> selectProject(
            @RequestBody SelectProjectRequest request,
            HttpServletResponse response) {

        PartialOnboardingSession partial = oAuthSessionStore.getPartial(request.partialSessionToken());
        boolean isReconfigure = partial.isReconfigure();

        OAuthCallbackResult.NewUserSingleProject result = oAuthSessionService.selectProject(
                request.projectRef(), request.partialSessionToken());

        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.createOnboardingCookie(result.sessionToken()).toString());

        return ResponseEntity.ok(Map.of("step", "CONFIRM_SCHEMA"));
    }

}
