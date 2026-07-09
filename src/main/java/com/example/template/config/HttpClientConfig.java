package com.example.template.config;

import java.time.Duration;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Outbound HTTP client. Built from Boot's auto-configured {@link RestClient.Builder} so it keeps
 * metrics ({@code http.client.requests}) and W3C trace-context propagation, with explicit connect
 * and read timeouts so a slow upstream can never hang a request thread indefinitely.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(Duration.ofSeconds(5));
        return builder
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }
}
