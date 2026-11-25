package com.broadcastmail.api.config;

import com.broadcastmail.common.unsubscribe.UnsubscribeTokenCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UnsubscribeConfig {

    /** Fails startup, not every unsubscribe, when UNSUBSCRIBE_SECRET is unset or shorter than 32 bytes. */
    @Bean
    public UnsubscribeTokenCodec unsubscribeTokenCodec(AppProperties appProperties) {
        return new UnsubscribeTokenCodec(appProperties.unsubscribeSecret());
    }
}
