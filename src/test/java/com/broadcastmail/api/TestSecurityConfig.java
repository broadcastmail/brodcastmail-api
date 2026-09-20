package com.broadcastmail.api;

import com.broadcastmail.api.security.ApiKeyAuthFilter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    SecurityFilterChain testFilterChain(
            HttpSecurity http,
            ApiKeyAuthFilter apiKeyAuthFilter) {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .addFilterBefore(
                        apiKeyAuthFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}