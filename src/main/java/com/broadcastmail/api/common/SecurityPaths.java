package com.broadcastmail.api.common;

import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class SecurityPaths {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/**",
            "/api/v1/oauth/**",
            "/api/v1/onboarding/**",
            "/actuator/health"
    );

    public static String[] publicPaths() {
        return PUBLIC_PATHS.toArray(String[]::new);
    }
}
