package com.broadcastmail.api.onboarding.reconfigure;

import com.broadcastmail.api.auth.CookieService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Completes a reconfigure (full OAuth re-setup) session. Lives under /connections for URL compatibility. */
@RestController
@RequestMapping("/api/v1/connections")
@RequiredArgsConstructor
public class ReconfigureController {

    private final ReconfigureService reconfigureService;
    private final CookieService cookieService;

    /**
     * 204, not a redirect: the frontend calls this with XHR cross-origin and navigates itself. A 302 back to the
     * frontend origin would be followed by the XHR and fail CORS, surfacing an error after a successful reconfigure.
     */
    @PostMapping("/reconfigure")
    public ResponseEntity<Void> reconfigure(
            @CookieValue(name = "onboarding_session", required = false) String sessionToken) {
        if (sessionToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        reconfigureService.reconfigure(sessionToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieService.clearOnboardingCookie().toString())
                .build();
    }
}
