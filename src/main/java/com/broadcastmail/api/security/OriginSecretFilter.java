package com.broadcastmail.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Closes the side door around Cloudflare: Railway's *.up.railway.app address reaches this service directly, skipping
 * Cloudflare's DDoS protection and rate limits. A Cloudflare Transform Rule adds X-Origin-Secret to every proxied
 * request; anything without it is refused here.
 *
 * <p>Off while ORIGIN_SECRET is unset, so deploying this changes nothing. Set the Cloudflare rule first, then the
 * env var: the other way round refuses all traffic. The health check is exempt, since Railway calls it directly.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OriginSecretFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Origin-Secret";
    private static final String HEALTH = "/actuator/health";

    private final byte[] secret;

    public OriginSecretFilter(@Value("${app.origin-secret:}") String secret) {
        this.secret = secret.isBlank() ? null : secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return secret == null || request.getRequestURI().startsWith(HEALTH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String sent = request.getHeader(HEADER);
        if (sent == null || !MessageDigest.isEqual(secret, sent.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }
}
