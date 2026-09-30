package com.broadcastmail.api.security;

import com.broadcastmail.api.account.plan.PlanFacade;
import com.broadcastmail.api.account.plan.PlanRequired;
import com.broadcastmail.api.common.SecurityPaths;
import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PlanEnforcementFilter extends OncePerRequestFilter {

    private final AccountRepository accountRepository;
    private final PlanFacade planFacade;
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        PlanRequired planRequired = resolvePlanRequired(request);
        if (planRequired != null) {
            Account account = resolveAccount();
            if (account != null) {
                planFacade.enforceFeature(account, planRequired.value());
            }
        }

        filterChain.doFilter(request, response);
    }

    private PlanRequired resolvePlanRequired(HttpServletRequest request) {
        try {
            HandlerExecutionChain chain = requestMappingHandlerMapping.getHandler(request);
            if (chain != null && chain.getHandler() instanceof HandlerMethod method) {
                return method.getMethodAnnotation(PlanRequired.class);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Account resolveAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UUID accountId)) {
            return null;
        }
        return accountRepository.findById(accountId).orElse(null);
    }
}