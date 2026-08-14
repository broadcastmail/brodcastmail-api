package com.broadcastmail.api.config;

import com.broadcastmail.api.common.SecurityPaths;
import com.broadcastmail.api.common.exceptions.SecurityConfigurationException;
import com.broadcastmail.api.security.ApiKeyAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // CSRF only matters for the bm_session cookie: a browser can be tricked into carrying
    // it cross-site. An X-API-Key header can't be attached by a cross-site page without
    // CORS approval, so header-authenticated requests never need a CSRF token.
    private static final RequestMatcher CSRF_REQUIRED_MATCHER = request ->
            CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request)
                    && !StringUtils.hasText(request.getHeader("X-API-Key"));

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain publicFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource) {
        try {
            http
                    .securityMatcher(SecurityPaths.publicPaths())
                    .cors(cors -> cors.configurationSource(corsConfigurationSource))
                    .csrf(AbstractHttpConfigurer::disable) // NOSONAR — public endpoints
                    .authorizeHttpRequests(auth -> auth
                            .anyRequest().permitAll());
            return http.build();
        } catch (Exception e) {
            throw new SecurityConfigurationException("Failed to configure public filter chain", e);
        }
    }

    @Bean
    @Order(2)
    @Profile("!test")
    public SecurityFilterChain apiFilterChain(
            HttpSecurity http,
            ApiKeyAuthFilter apiKeyAuthFilter,
            CorsConfigurationSource corsConfigurationSource) {
        try {
            http
                    .cors(cors -> cors.configurationSource(corsConfigurationSource))
                    .csrf(csrf -> csrf
                            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()) // NOSONAR
                            .requireCsrfProtectionMatcher(CSRF_REQUIRED_MATCHER) // NOSONAR
                            .ignoringRequestMatchers("/api/v1/oauth/supabase/**")) // NOSONAR
                    .sessionManagement(session -> session
                            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth
                            .anyRequest().authenticated())
                    .addFilterBefore(
                            apiKeyAuthFilter,
                            UsernamePasswordAuthenticationFilter.class);
            return http.build();
        } catch (Exception e) {
            throw new SecurityConfigurationException("Failed to configure API filter chain", e);
        }
    }
}
