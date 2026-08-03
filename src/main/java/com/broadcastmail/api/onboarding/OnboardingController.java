package com.broadcastmail.api.onboarding;

import com.broadcastmail.api.common.exceptions.InvalidOnboardingSessionException;
import com.broadcastmail.api.connection.SchemaService;
import com.broadcastmail.api.connection.dto.SchemaIntrospectionResult;
import com.broadcastmail.api.emailprovider.EmailProviderService;
import com.broadcastmail.api.emailprovider.dto.EmailProviderRequest;
import com.broadcastmail.api.onboarding.dto.OnboardingStatusResponse;
import com.broadcastmail.api.onboarding.dto.SchemaConfirmRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {
    private final EmailProviderService emailProviderService;
    private final OnboardingService onboardingService;
    private final OnboardingSessionStore onboardingSessionStore;
    private final SchemaService schemaService;
    @Value("${app.frontend.url}")
    private String frontendUrl;

    @PostMapping("/email-provider")
    public ResponseEntity<Void> addEmailProvider(
            @CookieValue(name = "onboarding_session", required = false) String sessionToken,
            @RequestBody @Valid EmailProviderRequest request) {
        emailProviderService.addEmailProvider(
                sessionToken,
                request.apiKey(),
                request.fromAddress()
        );
        return ResponseEntity.ok().build();
    }

    @PostMapping("/complete")
    public ResponseEntity<Void> complete(
            @CookieValue(name = "onboarding_session", required = false) String sessionToken,
            HttpServletResponse response) {
        if (sessionToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        onboardingService.completeOnboarding(sessionToken);
        clearSessionCookie(response);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, frontendUrl + "/dashboard")
                .build();
    }
    @SuppressWarnings("java:S6863") // status endpoint always returns 200 by design — step is never an error
    @GetMapping("/status")
    public ResponseEntity<OnboardingStatusResponse> status(
            @CookieValue(name = "onboarding_session", required = false) String sessionToken) {

        if (sessionToken == null) {
            return ResponseEntity.ok(new OnboardingStatusResponse(OnboardingStep.CONNECT_SUPABASE));
        }
        try {
            OnboardingSession session = onboardingSessionStore.get(sessionToken);

            if (session.getSchemaDetails() == null || !session.getSchemaDetails().confirmed()) {
                return ResponseEntity.ok(new OnboardingStatusResponse(OnboardingStep.CONFIRM_SCHEMA));
            }
            if (session.getResendDetails() == null) {
                return ResponseEntity.ok(new OnboardingStatusResponse(OnboardingStep.CONNECT_RESEND));
            }
            return ResponseEntity.ok(new OnboardingStatusResponse(OnboardingStep.CONFIRM_ACCOUNT));

        } catch (InvalidOnboardingSessionException _) {
            return ResponseEntity.ok(new OnboardingStatusResponse(OnboardingStep.CONNECT_SUPABASE));
        }
    }

    private void clearSessionCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("onboarding_session", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .maxAge(0)
                .path("/")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @PostMapping("/schema/confirm")
    public ResponseEntity<Void> confirmSchema(
            @CookieValue(name = "onboarding_session", required = false) String sessionToken,
            @RequestBody @Valid SchemaConfirmRequest request) {
        if (sessionToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        schemaService.confirm(sessionToken, request.columnNames());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/schema")
    public ResponseEntity<SchemaIntrospectionResult> detectSchema(
            @CookieValue(name = "onboarding_session", required = false) String sessionToken) {
        SchemaIntrospectionResult result = schemaService.detect(sessionToken);
        return ResponseEntity.ok(result);
    }
}
