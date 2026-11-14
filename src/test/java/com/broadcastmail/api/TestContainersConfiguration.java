package com.broadcastmail.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;


@TestConfiguration(proxyBeanMethods = false)
public class TestContainersConfiguration {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    static {
        POSTGRES.start();
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return POSTGRES;
    }

    @DynamicPropertySource
    static void directDatasource(DynamicPropertyRegistry registry) {
        registry.add("app.datasource.direct.url", POSTGRES::getJdbcUrl);
        registry.add("app.datasource.direct.username", POSTGRES::getUsername);
        registry.add("app.datasource.direct.password", POSTGRES::getPassword);
    }
}
