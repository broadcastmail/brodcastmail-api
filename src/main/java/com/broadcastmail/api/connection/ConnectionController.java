package com.broadcastmail.api.connection;

import com.broadcastmail.api.auth.CookieService;
import com.broadcastmail.api.connection.dto.SchemaIntrospectionResult;
import com.broadcastmail.api.oauth.dto.ProjectOption;
import com.broadcastmail.api.onboarding.dto.SchemaConfirmRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.broadcastmail.api.connection.dto.ConnectionRequests;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/connections")
@RequiredArgsConstructor
public class ConnectionController {

    private final SchemaService schemaService;
    private final ConnectionReconfigureService connectionReconfigureService;
    private final ConnectionQueryService connectionQueryService;
    private final ConnectionUpdateService connectionUpdateService;
    private final CookieService cookieService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    // ── Reconfigure (full OAuth re-setup) ──────────────────────────────────

    @GetMapping("/schema")
    public ResponseEntity<SchemaIntrospectionResult> detectSchema(
            @CookieValue(name = "onboarding_session", required = false) String sessionToken) {
        return ResponseEntity.ok(schemaService.detect(sessionToken));
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

    @GetMapping("/schema/reconnect")
    public ResponseEntity<SchemaIntrospectionResult> detectSchemaForReconnect(
            @AuthenticationPrincipal UUID accountId,
            @RequestParam(required = false) String projectRef) {
        return ResponseEntity.ok(schemaService.detectForAccount(accountId, projectRef));
    }

    @PatchMapping("/project")
    public ResponseEntity<Void> updateProject(
            @AuthenticationPrincipal UUID accountId,
            @RequestBody @Valid ConnectionRequests.UpdateProjectRequest request) {
        connectionUpdateService.updateProject(accountId, request.projectRef());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/table")
    public ResponseEntity<Void> updateTable(
            @AuthenticationPrincipal UUID accountId,
            @RequestBody @Valid ConnectionRequests.UpdateTableRequest request) {
        connectionUpdateService.updateTable(accountId, request.userTableSchema(), request.userTableName());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/email-column")
    public ResponseEntity<Void> updateEmailColumn(
            @AuthenticationPrincipal UUID accountId,
            @RequestBody @Valid ConnectionRequests.UpdateEmailColumnRequest request) {
        connectionUpdateService.updateEmailColumn(accountId, request.emailColumn());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/columns")
    public ResponseEntity<Void> updateColumns(
            @AuthenticationPrincipal UUID accountId,
            @RequestBody @Valid ConnectionRequests.UpdateColumnsRequest request) {
        connectionUpdateService.updateColumns(accountId, request.columnNames());
        return ResponseEntity.ok().build();
    }
}