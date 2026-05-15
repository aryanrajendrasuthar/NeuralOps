package com.neuralops.alertservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder(
            @Value("${neuralops.alerting.webhook-timeout-seconds:10}") int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int millis = (int) Duration.ofSeconds(timeoutSeconds).toMillis();
        factory.setConnectTimeout(millis);
        factory.setReadTimeout(millis);

        return RestClient.builder().requestFactory(factory);
    }
}
