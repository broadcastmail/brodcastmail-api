package com.broadcastmail.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class ResendPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(ResendProperties.class)
    static class Config {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @Test
    void shouldFailStartupWhenWebhookBaseUrlIsMissing() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldFailStartupWhenWebhookBaseUrlIsUnresolvedPlaceholder() {
        runner.withPropertyValues("app.resend.webhook-base-url=${RESEND_WEBHOOK_BASE_URL}")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldStartWhenWebhookBaseUrlIsSet() {
        runner.withPropertyValues("app.resend.webhook-base-url=https://broadcast-webhooks-dev.up.railway.app")
                .run(context -> assertThat(context).hasNotFailed()
                        .getBean(ResendProperties.class).extracting(ResendProperties::webhookBaseUrl)
                        .isEqualTo("https://broadcast-webhooks-dev.up.railway.app"));
    }
}
