package com.desitech.vyaparsathi.einvoice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate wired for NIC IRP calls. Only registered when {@code
 * einvoice.provider=NIC}; keeps the bean graph clean when running with
 * the mock provider (no unused HTTP client on the classpath).
 *
 * The bean name is {@code nicRestTemplate} but it's also the primary
 * RestTemplate — no other RestTemplate exists in this application.
 */
@Configuration
@ConditionalOnProperty(prefix = "einvoice", name = "provider", havingValue = "NIC")
public class NicHttpConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, EInvoiceProperties props) {
        int timeoutMs = props.getNic().getTimeoutMs();
        return builder
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .readTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }
}
