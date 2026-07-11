package com.broadcastemail.api.security;

import com.broadcastemail.api.account.AccountRepository;
import com.broadcastemail.api.common.SecurityUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {
    private final AccountRepository accountRepository;
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/") ||
                path.equals("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
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
        filterChain.doFilter(request, response);
    }
}
