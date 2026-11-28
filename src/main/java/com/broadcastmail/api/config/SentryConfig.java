package com.broadcastmail.api.config;

import com.broadcastmail.common.sentry.SentryEmailScrubber;
import io.sentry.SentryOptions;
import io.sentry.protocol.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.UUID;

@Configuration
public class SentryConfig {

    @Bean
    SentryOptions.BeforeSendCallback sentryBeforeSend(
            @Value("${app.e2e-account-ids:}") Set<UUID> e2eAccountIds) {
        return (event, hint) -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UUID accountId) {
                if (e2eAccountIds.contains(accountId)) return null;
                User user = new User();
                user.setId(accountId.toString());
                event.setUser(user);
            }
            return SentryEmailScrubber.scrub(event);
        };
    }
}
