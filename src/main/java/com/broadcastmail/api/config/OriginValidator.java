package com.broadcastmail.api.config;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.AntPathMatcher;

@Component
public class OriginValidator {

    private final AppProperties appProperties;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public OriginValidator(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public void validate(String origin) {
        boolean allowed = appProperties.frontend().allowedOrigins().stream()
                .anyMatch(pattern -> matcher.match(pattern, origin));
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Origin not allowed: " + origin);
        }
    }

    public String validateAndReturn(jakarta.servlet.http.HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Origin header");
        validate(origin);
        return origin;
    }
}
