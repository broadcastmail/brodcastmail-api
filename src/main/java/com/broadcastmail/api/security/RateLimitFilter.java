package com.broadcastmail.api.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Per-account and per-IP request limits, answered with 429 + Retry-After. Runs after Spring Security, so an
 * authenticated request is counted by its account; anything else by client IP. The first matching rule wins.
 *
 * <p>Buckets live in memory, which is right for the single API instance. With several instances each would count
 * on its own; Bucket4j's Redis/Postgres proxy managers replace the cache below without touching the rules.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    enum Key { ACCOUNT, IP }

    record Rule(String method, String pathPattern, Key key, Bandwidth limit) {
    }

    static final List<Rule> RULES = List.of(
            // sends a real email to the account owner
            new Rule("POST", "/api/v1/campaigns/*/test/send", Key.ACCOUNT, perHour(10)),
            // each one queries the tenant's database
            new Rule("*", "/api/v1/campaigns/*/preview", Key.ACCOUNT, perMinute(60)),
            new Rule("*", "/api/v1/oauth/**", Key.IP, perMinute(30)),
            new Rule("*", "/api/v1/onboarding/**", Key.IP, perMinute(30)),
            new Rule("*", "/unsubscribe", Key.IP, perMinute(30)),
            new Rule("POST", "/api/v1/feedback", Key.ACCOUNT, perHour(20)),
            new Rule("POST", "/api/v1/public/feedback", Key.IP, perHour(10)),
            new Rule("*", "/api/v1/**", Key.ACCOUNT, perMinute(300)));

    private static final AntPathMatcher PATHS = new AntPathMatcher();

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .maximumSize(100_000)
            .build();

    private final boolean trustCfConnectingIp;

    /**
     * {@code trustCfConnectingIp}: behind Cloudflare every request arrives from a Cloudflare IP and the client's is in
     * CF-Connecting-IP. Turn it on only once the origin accepts nothing but Cloudflare (PRELAUNCH §7), or anyone can
     * set the header and pick which IP gets counted.
     */
    public RateLimitFilter(@Value("${app.rate-limit.trust-cf-connecting-ip:false}") boolean trustCfConnectingIp) {
        this.trustCfConnectingIp = trustCfConnectingIp;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Rule rule = RULES.stream().filter(r -> matches(r, request)).findFirst().orElse(null);
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }

        String bucketKey = rule.pathPattern() + "|" + clientKey(rule.key(), request);
        Bucket bucket = buckets.get(bucketKey, _ -> Bucket.builder().addLimit(rule.limit()).build());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Too many requests\"}");
    }

    private static boolean matches(Rule rule, HttpServletRequest request) {
        return (rule.method().equals("*") || rule.method().equals(request.getMethod()))
                && PATHS.match(rule.pathPattern(), request.getRequestURI());
    }

    /** An ACCOUNT rule falls back to the IP when nobody is signed in. */
    private String clientKey(Key key, HttpServletRequest request) {
        if (key == Key.ACCOUNT) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UUID accountId) return "account:" + accountId;
        }
        return "ip:" + clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        String cf = request.getHeader("CF-Connecting-IP");
        return trustCfConnectingIp && cf != null && !cf.isBlank() ? cf.strip() : request.getRemoteAddr();
    }

    private static Bandwidth perMinute(long requests) {
        return Bandwidth.builder().capacity(requests).refillGreedy(requests, Duration.ofMinutes(1)).build();
    }

    private static Bandwidth perHour(long requests) {
        return Bandwidth.builder().capacity(requests).refillGreedy(requests, Duration.ofHours(1)).build();
    }
}
