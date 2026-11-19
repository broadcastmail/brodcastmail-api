package com.broadcastmail.api.oauth;

import com.broadcastmail.api.auth.CookieService;
import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.common.exceptions.OAuthStateValidationException;
import com.broadcastmail.api.config.AppProperties;
import com.broadcastmail.api.config.OriginValidator;
import com.broadcastmail.api.oauth.dto.OAuthCallbackResult;
import com.broadcastmail.api.oauth.dto.ProjectOption;
import com.broadcastmail.api.oauth.dto.SelectProjectRequest;
import com.broadcastmail.api.onboarding.PartialOnboardingSession;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
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
    private final AppProperties appProperties;
    private final OriginValidator originValidator;

    private static final String COOKIE_VERIFIER = "oauth_code_verifier";
    private static final String COOKIE_STATE = "oauth_state";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${supabase.oauth.client-id}")
    private String clientId;

    @Value("${supabase.oauth.redirect-uri}")
    private String redirectUri;

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(
            @RequestParam(required = false) String intent,
            @RequestParam(required = false) String redirectOrigin,
            HttpServletResponse response) {

        if (redirectOrigin != null) originValidator.validate(redirectOrigin);

        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);
        String stateId = UUID.randomUUID().toString().replace("-", "");
        String state = stateId + ":" + (intent != null ? intent : "new_user");

        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.createPkceCookie(COOKIE_VERIFIER, codeVerifier).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.createPkceCookie(COOKIE_STATE, state).toString());
        if (redirectOrigin != null) {
            response.addHeader(HttpHeaders.SET_COOKIE,
                    cookieService.createPkceOriginCookie(redirectOrigin).toString());
        }

        String authorizeUrl = UriComponentsBuilder
                .fromUriString("https://api.supabase.com/v1/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "projects:read database:write")
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .toUriString();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, authorizeUrl)
                .build();
    }

    @Operation(operationId = "oauthCallback")
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam String code,
            @RequestParam String state,
            HttpServletRequest request,
            HttpServletResponse response) {

        String storedState = cookieValue(request, COOKIE_STATE);
        String codeVerifier = cookieValue(request, COOKIE_VERIFIER);

        if (storedState == null || codeVerifier == null || !storedState.equals(state)) {
            throw new OAuthStateValidationException();
        }

        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.clearPkceCookie(COOKIE_VERIFIER).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.clearPkceCookie(COOKIE_STATE).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.clearPkceOriginCookie().toString());

        int colonIdx = state.indexOf(':');
        String intent = colonIdx >= 0 ? state.substring(colonIdx + 1) : "new_user";
        boolean isReconfigure = intent.startsWith("reconfigure");

        String origin = cookieValue(request, "oauth_redirect_origin");
        if (origin == null) throw new OAuthStateValidationException();

        UUID accountId = null;
        if (isReconfigure) {
            String sessionValue = cookieValue(request, "bm_session");
            if (sessionValue != null) {
                accountId = accountRepository.findByApiKeyHash(SecurityUtil.sha256(sessionValue))
                        .map(Account::getId)
                        .orElse(null);
            }
        }

        OAuthCallbackResult result = oAuthSupabaseService.exchangeWithPKCE(code, codeVerifier, accountId);

        return switch (result) {
            case OAuthCallbackResult.ReturningUser(String apiKey) -> {
                response.addHeader(HttpHeaders.SET_COOKIE,
                        cookieService.createSessionCookie(apiKey).toString());
                yield ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, origin + "/dashboard")
                        .build();
            }
            case OAuthCallbackResult.NewUserSingleProject(String token) -> {
                response.addHeader(HttpHeaders.SET_COOKIE,
                        cookieService.createOnboardingCookie(token).toString());
                yield ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, origin + "/onboarding/email-provider")
                        .build();
            }
            case OAuthCallbackResult.NewUserMultipleProjects(String partial) ->
                    ResponseEntity.status(HttpStatus.FOUND)
                            .header(HttpHeaders.LOCATION,
                                    origin + "/onboarding/select-project?partialToken=" + partial)
                            .build();
            case OAuthCallbackResult.ReconfigureSingleProject(String token) -> {
                response.addHeader(HttpHeaders.SET_COOKIE,
                        cookieService.createOnboardingCookie(token).toString());
                String dest = "reconfigure_popup".equals(intent)
                        ? origin + "/settings/reconfigure/oauth-popup-done"
                        : origin + "/settings/reconfigure/schema";
                yield ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, dest)
                        .build();
            }
            case OAuthCallbackResult.ReconfigureMultipleProjects(String partial) -> {
                String dest = "reconfigure_popup".equals(intent)
                        ? origin + "/settings/reconfigure/oauth-popup-done"
                        : origin + "/settings/reconfigure/select-project?partialToken=" + partial;
                yield ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, dest)
                        .build();
            }
        };
    }

    @GetMapping("/projects")
    public ResponseEntity<List<ProjectOption>> listProjects(@RequestParam String partialToken) {
        return ResponseEntity.ok(oAuthSessionService.listPartialProjects(partialToken));
    }

    @PostMapping("/select-project")
    public ResponseEntity<Void> selectProject(
            @RequestBody SelectProjectRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        PartialOnboardingSession partial = oAuthSessionStore.getPartial(request.partialSessionToken());
        boolean isReconfigure = partial.isReconfigure();

        OAuthCallbackResult.NewUserSingleProject result = oAuthSessionService.selectProject(
                request.projectRef(), request.partialSessionToken());

        String origin = originValidator.validateAndReturn(httpRequest);
        String redirectUrl = isReconfigure
                ? origin + "/settings/reconfigure/schema"
                : origin + "/onboarding/email-provider";

        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.createOnboardingCookie(result.sessionToken()).toString());

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirectUrl)
                .build();
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        return Arrays.stream(cookies)
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private static String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String generateCodeChallenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
