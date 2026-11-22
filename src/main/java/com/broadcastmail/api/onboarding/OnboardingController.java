package com.broadcastmail.api.onboarding;

import com.broadcastmail.api.onboarding.steps.OnboardingEmailSourceService;
import com.broadcastmail.api.onboarding.steps.OnboardingSchemaService;
import com.broadcastmail.api.auth.CookieService;
import com.broadcastmail.api.common.exceptions.InvalidOnboardingSessionException;
import com.broadcastmail.api.config.OriginValidator;
import com.broadcastmail.api.connection.dto.EmailSourceResult;
import com.broadcastmail.api.connection.dto.SchemaIntrospectionResult;
import com.broadcastmail.api.emailprovider.EmailProviderService;
import com.broadcastmail.api.emailprovider.dto.EmailProviderRequest;
import com.broadcastmail.api.emailprovider.dto.VerifyKeyRequest;
import com.broadcastmail.api.emailprovider.dto.VerifyKeyResponse;
import com.broadcastmail.api.oauth.OAuthSessionStore;
import com.broadcastmail.api.onboarding.dto.ConfirmEmailTableRequest;
import com.broadcastmail.api.onboarding.dto.OnboardingStatusResponse;
import com.broadcastmail.api.onboarding.dto.RecapData;
import com.broadcastmail.api.onboarding.dto.SelectColumnsRequest;
import com.broadcastmail.api.onboarding.dto.SelectTableRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;


@Tag(name = "onboarding")
@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {
    private final EmailProviderService emailProviderService;
    private final OnboardingService onboardingService;
    private final OAuthSessionStore onboardingSessionStore;
    private final OnboardingSchemaService onboardingSchemaService;
    private final OnboardingEmailSourceService onboardingEmailSourceService;
    private final CookieService cookieService;
    private final OriginValidator originValidator;


    @PostMapping("/complete")
    public ResponseEntity<Void> complete(
            @CookieValue(name = "onboarding_session", required = false) String sessionToken,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (sessionToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String origin = originValidator.validateAndReturn(request);
        String rawApiKey = onboardingService.completeOnboarding(sessionToken);
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.createSessionCookie(rawApiKey).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.clearOnboardingCookie().toString());
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, origin + "/dashboard")
                .build();
    }

    @DeleteMapping("/session")
    public ResponseEntity<Void> abandonSession(
            @CookieValue(name = "onboarding_session", required = false) String sessionToken,
            HttpServletResponse response) {
        if (sessionToken != null) {
            try {
                onboardingSessionStore.invalidate(sessionToken);
            } catch (InvalidOnboardingSessionException _) {
                // already gone — that's fine
            }
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookieService.clearOnboardingCookie().toString());
        return ResponseEntity.noContent().build();
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
            if (!session.isEmailTableConfirmed()) {
                return ResponseEntity.ok(new OnboardingStatusResponse(OnboardingStep.CONFIRM_EMAIL_TABLE));
            }
            if (session.getResendDetails() == null) {
                return ResponseEntity.ok(new OnboardingStatusResponse(OnboardingStep.CONNECT_RESEND));
            }
            return ResponseEntity.ok(new OnboardingStatusResponse(OnboardingStep.CONFIRM_ACCOUNT,
                    RecapData.from(session)));

        } catch (InvalidOnboardingSessionException _) {
            return ResponseEntity.ok(new OnboardingStatusResponse(OnboardingStep.CONNECT_SUPABASE));
        }
    }

    @GetMapping("/schema")
    public ResponseEntity<SchemaIntrospectionResult> detectSchema(
            @CookieValue(name = "onboarding_session", required = false) String sessionToken) {
        SchemaIntrospectionResult result = onboardingSchemaService.detect(sessionToken);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/schema/select-table")
    public ResponseEntity<SchemaIntrospectionResult.Detected> selectTable(
            @CookieValue(name = "onboarding_session", required = false) String sessionToken,
            @RequestBody SelectTableRequest request) {
        if (sessionToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (request.skipRequested()) {
            onboardingSchemaService.skipProfileTable(sessionToken);
            return ResponseEntity.ok().build();
        }
        SchemaIntrospectionResult.Detected result =
                onboardingSchemaService.selectTable(sessionToken, request.userTableSchema(), request.userTableName());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/schema/test")
    public ResponseEntity<Void> testConnection(@CookieValue(name = "onboarding_session", required = false) String sessionToken) {
        if (sessionToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        onboardingService.testConnection(sessionToken);
        return ResponseEntity.noContent().build();
    }


    @PostMapping("/schema/select-columns")
    public ResponseEntity<Void> confirmSchema(
            @CookieValue(name = "onboarding_session", required = false) String sessionToken,
            @RequestBody @Valid SelectColumnsRequest request) {
        if (sessionToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        onboardingSchemaService.selectColumns(sessionToken, request.columnNames());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/email/source")
    public ResponseEntity<EmailSourceResult> detectEmailSource(
            @CookieValue(name = "onboarding_session", required = false) String sessionToken) {
        return ResponseEntity.ok(onboardingEmailSourceService.detect(sessionToken));
    }

    @PostMapping("/email/select-source")
    public ResponseEntity<Void> confirmEmailTable(
            @CookieValue(name = "onboarding_session", required = false) String sessionToken,
            @RequestBody(required = false) ConfirmEmailTableRequest request) {
        if (sessionToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        onboardingEmailSourceService.confirm(sessionToken, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/email/provider/verify-key")
    public ResponseEntity<VerifyKeyResponse> verifyResendKey(
            @CookieValue(name = "onboarding_session", required = false) String sessionToken,
            @RequestBody @Valid VerifyKeyRequest request) {
        if (sessionToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(emailProviderService.verifyKeyAndStore(sessionToken, request.apiKey()));
    }

    @GetMapping("/email/provider/domains")
    public ResponseEntity<VerifyKeyResponse> listResendDomains(
            @CookieValue(name = "onboarding_session", required = false) String sessionToken) {
        if (sessionToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(emailProviderService.listDomainsFromSession(sessionToken));
    }

    @PostMapping("/email/provider")
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

    @PostMapping("/email/provider/resend/test")
    public ResponseEntity<Void> testResendConnection(
            @CookieValue(name = "onboarding_session", required = false) String sessionToken) {
        if (sessionToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        emailProviderService.testResendConnection(sessionToken);
        return ResponseEntity.noContent().build();
    }
}
