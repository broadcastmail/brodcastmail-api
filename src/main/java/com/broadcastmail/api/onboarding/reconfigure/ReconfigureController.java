package com.broadcastmail.api.onboarding.reconfigure;

import com.broadcastmail.api.auth.CookieService;
import com.broadcastmail.api.config.OriginValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private final OriginValidator originValidator;

    @PostMapping("/reconfigure")
    public ResponseEntity<Void> reconfigure(
            @CookieValue(name = "onboarding_session", required = false) String sessionToken,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (sessionToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        reconfigureService.reconfigure(sessionToken);
        String origin = originValidator.validateAndReturn(request);
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.clearOnboardingCookie().toString());
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, origin + "/settings")
                .build();
    }
}
