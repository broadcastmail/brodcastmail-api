package com.broadcastmail.api.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient supabaseRestClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(30, TimeUnit.SECONDS)));
        return RestClient.builder()
                .baseUrl("https://api.supabase.com")
                .requestFactory(new ReactorClientHttpRequestFactory(httpClient))
                .build();
    }

    @Bean
    public RestClient resendRestClient() {
        return RestClient.builder()
                .baseUrl("https://api.resend.com")
                .build();
    }
}
