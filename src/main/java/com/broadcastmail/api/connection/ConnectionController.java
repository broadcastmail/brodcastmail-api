package com.broadcastmail.api.connection;

import com.broadcastmail.api.auth.CookieService;
import com.broadcastmail.api.connection.dto.SchemaIntrospectionResult;
import com.broadcastmail.api.oauth.dto.ProjectOption;
import com.broadcastmail.api.connection.dto.EmailSourceResult;
import com.broadcastmail.api.emailprovider.EmailProviderService;
import com.broadcastmail.api.emailprovider.dto.EmailProviderInfoResponse;
import com.broadcastmail.api.emailprovider.dto.ReconfigureProviderRequest;
import com.broadcastmail.api.emailprovider.dto.UpdateSenderRequest;
import com.broadcastmail.api.emailprovider.dto.VerifyKeyRequest;
import com.broadcastmail.api.emailprovider.dto.VerifyKeyResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.broadcastmail.api.connection.dto.ConnectionRequests;
import com.broadcastmail.api.connection.dto.FilterableColumnResponse;
import com.broadcastmail.api.connection.dto.FilterContextResponse;
import com.broadcastmail.api.connection.dto.ReconnectCompatibilityResponse;
import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/connections")
@RequiredArgsConstructor
public class ConnectionController {

    private final SchemaService schemaService;
    private final EmailSourceService emailSourceService;
    private final ConnectionReconfigureService connectionReconfigureService;
    private final ConnectionQueryService connectionQueryService;
    private final ConnectionUpdateService connectionUpdateService;
    private final CookieService cookieService;
    private final EmailProviderService emailProviderService;
    private final FilterContextService filterContextService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    // ── Reconfigure (full OAuth re-setup) ──────────────────────────────────
    @PostMapping("/reconfigure")
    public ResponseEntity<Void> reconfigure(
            @CookieValue(name = "onboarding_session", required = false) String sessionToken,
            HttpServletResponse response) {
        if (sessionToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        connectionReconfigureService.reconfigure(sessionToken);
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.clearOnboardingCookie().toString());
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, frontendUrl + "/settings")
                .build();
    }

    // ── Reconnect (edit fields without OAuth) ──────────────────────────────

    @GetMapping("/supabase/projects")
    public ResponseEntity<List<ProjectOption>> listProjects(
            @AuthenticationPrincipal UUID accountId) {
        return ResponseEntity.ok(connectionQueryService.listProjects(accountId));
    }

    // Reconnect
    @PatchMapping("/reconnect/project")
    public ResponseEntity<Void> updateProject(
            @AuthenticationPrincipal UUID accountId,
            @RequestBody @Valid ConnectionRequests.UpdateProjectRequest request) {
        connectionUpdateService.updateProject(accountId, request.projectRef());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/reconnect/user-schema")
    public ResponseEntity<Void> updateSchema(
            @AuthenticationPrincipal UUID accountId,
            @RequestBody @Valid ConnectionRequests.UpdateTableRequest request) {
        connectionUpdateService.updateSchemaTable(accountId, request.userTableSchema(), request.userTableName(), request.userIdColumn());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/reconnect/email-source")
    public ResponseEntity<Void> updateEmailTable(
            @AuthenticationPrincipal UUID accountId,
            @RequestBody ConnectionRequests.UpdateEmailTableRequest request) {
        connectionUpdateService.updateEmailTable(accountId, request.emailTableSchema(), request.emailTableName(), request.emailColumn());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/reconnect/columns")
    public ResponseEntity<Void> updateColumns(
            @AuthenticationPrincipal UUID accountId,
            @RequestBody @Valid ConnectionRequests.UpdateColumnsRequest request) {
        connectionUpdateService.updateSchemaColumns(accountId, request.columnNames());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/reconnect/columns")
    public ResponseEntity<List<FilterableColumnResponse>> listColumnsForReconnect(
            @AuthenticationPrincipal UUID accountId) {
        return ResponseEntity.ok(connectionQueryService.listColumns(accountId));
    }

    @DeleteMapping("/reconnect/profile-columns")
    public ResponseEntity<Void> deleteProfileColumns(
            @AuthenticationPrincipal UUID accountId) {
        connectionUpdateService.deleteProfileColumns(accountId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reconfigure/compatibility")
    public ResponseEntity<ReconnectCompatibilityResponse> checkReconnectCompatibility(
            @AuthenticationPrincipal UUID accountId,
            @RequestParam String projectRef) {
        return ResponseEntity.ok(connectionQueryService.checkCompatibility(accountId, projectRef));
    }

    @GetMapping("/reconfigure/introspect")
    public ResponseEntity<SchemaIntrospectionResult> detectSchemaForReconnect(
            @AuthenticationPrincipal UUID accountId,
            @RequestParam(required = false) String projectRef) {
        return ResponseEntity.ok(schemaService.detectForAccount(accountId, projectRef));
    }


    @GetMapping("/filters")
    public ResponseEntity<FilterContextResponse> getFilterContext(@AuthenticationPrincipal UUID accountId) {
        return ResponseEntity.ok(filterContextService.build(accountId));
    }

    @GetMapping("/columns")
    public ResponseEntity<List<FilterableColumnResponse>> listColumns(
            @AuthenticationPrincipal UUID accountId) {
        return ResponseEntity.ok(connectionQueryService.listColumns(accountId));
    }

    @GetMapping("/email-source")
    public ResponseEntity<EmailSourceResult> detectEmailSource(
            @AuthenticationPrincipal UUID accountId) {
        return ResponseEntity.ok(emailSourceService.detectForAccount(accountId));
    }


    // ── Email provider ─────────────────────────────────────────────────────

    @GetMapping("/email-provider")
    public ResponseEntity<EmailProviderInfoResponse> getEmailProvider(
            @AuthenticationPrincipal UUID accountId) {
        return ResponseEntity.ok(emailProviderService.getEmailProviderInfo(accountId));
    }

    @GetMapping("/email-provider/domains")
    public ResponseEntity<VerifyKeyResponse> listEmailProviderDomains(
            @AuthenticationPrincipal UUID accountId) {
        return ResponseEntity.ok(emailProviderService.listDomains(accountId));
    }

    @PostMapping("/email-provider/verify")
    public ResponseEntity<VerifyKeyResponse> verifyEmailProviderKey(
            @AuthenticationPrincipal UUID accountId,
            @RequestBody @Valid VerifyKeyRequest request) {
        return ResponseEntity.ok(emailProviderService.verifyKey(request.apiKey()));
    }

    @PutMapping("/reconfigure/email-provider")
    public ResponseEntity<Void> reconfigureEmailProvider(
            @AuthenticationPrincipal UUID accountId,
            @RequestBody @Valid ReconfigureProviderRequest request) {
        emailProviderService.reconfigure(accountId, request.apiKey(), request.fromName(), request.fromAddress());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/reconfigure/email-provider/sender")
    public ResponseEntity<Void> updateEmailProviderSender(
            @AuthenticationPrincipal UUID accountId,
            @RequestBody @Valid UpdateSenderRequest request) {
        emailProviderService.updateSender(accountId, request.fromName(), request.fromAddress());
        return ResponseEntity.ok().build();
    }

}