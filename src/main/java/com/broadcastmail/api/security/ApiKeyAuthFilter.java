package com.broadcastmail.api.security;

import com.broadcastmail.api.common.SecurityPaths;
import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.common.account.AccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final AccountRepository accountRepository;
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

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

        String apiKey = request.getHeader("X-API-Key");

        if (apiKey == null || apiKey.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing API key");
            return;
        }

        String hashedKey = SecurityUtil.sha256(apiKey);

        accountRepository.findByApiKeyHash(hashedKey)
                .ifPresentOrElse(
                        account -> SecurityContextHolder.getContext()
                                .setAuthentication(new ApiKeyAuthentication(account.getId())),
                        () -> {
                            try {
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                );

        if (response.isCommitted()) return;
        chain.doFilter(request, response);
    }
}