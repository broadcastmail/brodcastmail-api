package com.broadcastmail.api.security;

import com.broadcastmail.api.auth.CookieService;
import com.broadcastmail.api.auth.SessionService;
import com.broadcastmail.api.common.SecurityPaths;
import com.broadcastmail.api.common.SecurityUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;

/** Authenticates every non-public request by its bm_session cookie, and refreshes the cookie when the session slid. */
@Component
@RequiredArgsConstructor
public class SessionAuthFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final SessionService sessionService;
    private final CookieService cookieService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return Arrays.stream(SecurityPaths.publicPaths())
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String token = SecurityUtil.getCookieValue(request, CookieService.SESSION_COOKIE);
        if (token == null || token.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing session");
            return;
        }

        Optional<SessionService.ValidSession> session = sessionService.validate(token);
        if (session.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid session");
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(new SessionAuthentication(session.get().accountId()));
        OffsetDateTime refreshed = session.get().refreshedExpiresAt();
        if (refreshed != null) {
            // The browser drops the cookie at its own Max-Age, so it has to slide with the session.
            response.addHeader(HttpHeaders.SET_COOKIE, cookieService.createSessionCookie(
                    token, Duration.between(OffsetDateTime.now(), refreshed)).toString());
        }
        chain.doFilter(request, response);
    }
}
