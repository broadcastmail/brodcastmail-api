package com.broadcastmail.api.account.plan;

import com.broadcastmail.common.account.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class PlanEnforcementAspect {

    private final AccountRepository accountRepository;
    private final PlanFacade planFacade;

    @Before("@annotation(planRequired)")
    public void enforce(PlanRequired planRequired) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UUID accountId)) return;
        accountRepository.findById(accountId).ifPresent(account ->
                planFacade.enforceFeature(account, planRequired.value()));
    }
}
