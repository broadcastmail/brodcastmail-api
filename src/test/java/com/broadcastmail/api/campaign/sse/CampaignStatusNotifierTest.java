package com.broadcastmail.api.campaign.sse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

/** Runs against a real Postgres: a mocked JdbcTemplate can't show how pg_notify's void result maps. */
class CampaignStatusNotifierTest {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @BeforeAll
    static void start() { POSTGRES.start(); }

    @AfterAll
    static void stop() { POSTGRES.stop(); }

    @Test
    void notifyDoesNotFailOnPgNotifyVoidResult() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var notifier = new CampaignStatusNotifier(new JdbcTemplate(dataSource));

        assertThatCode(() -> notifier.notify(UUID.randomUUID(), UUID.randomUUID())).doesNotThrowAnyException();
    }
}
