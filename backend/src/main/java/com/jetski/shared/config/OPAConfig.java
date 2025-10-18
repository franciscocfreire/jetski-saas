package com.jetski.shared.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuração do cliente OPA (Open Policy Agent).
 *
 * Cria WebClient configurado para comunicação com OPA Server.
 *
 * @author Jetski Team
 */
@Slf4j
@Configuration
public class OPAConfig {

    @Value("${jetski.opa.base-url:http://localhost:8181}")
    private String opaBaseUrl;

    @Value("${jetski.opa.timeout-seconds:5}")
    private int timeoutSeconds;

    /**
     * WebClient configurado para OPA
     */
    @Bean(name = "opaWebClient")
    public WebClient opaWebClient() {
        log.info("Configurando OPA WebClient: baseUrl={}, timeout={}s",
            opaBaseUrl, timeoutSeconds);

        return WebClient.builder()
            .baseUrl(opaBaseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .codecs(configurer -> configurer
                .defaultCodecs()
                .maxInMemorySize(1024 * 1024)) // 1MB buffer
            .build();
    }
}
