package com.broadcastmail.api.suppressions;

import com.broadcastmail.api.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnsubscribeTokenVerifierTest {

    private static UnsubscribeTokenVerifier verifierWith(String secret) {
        return new UnsubscribeTokenVerifier(new AppProperties(secret, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"${UNSUBSCRIBE_SECRET}", "too-short"})
    void refusesToStartWithAnUnsetOrShortSecret(String secret) {
        assertThatThrownBy(() -> verifierWith(secret).requireUsableSecret())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UNSUBSCRIBE_SECRET");
    }

    @Test
    void startsWithA32ByteSecret() {
        assertThatCode(() -> verifierWith("12345678901234567890123456789012").requireUsableSecret())
                .doesNotThrowAnyException();
    }
}
