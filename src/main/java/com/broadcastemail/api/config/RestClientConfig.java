package com.broadcastemail.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient supabaseRestClient() {
        return RestClient.builder()
                .baseUrl("https://api.supabase.com")
                .build();
    }

    @Bean
    public RestClient resendRestClient() {
        return RestClient.builder()
                .baseUrl("https://api.resend.com")
                .build();
    }
}
