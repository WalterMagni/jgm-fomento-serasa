package com.portal.serasa.infrastructure.integration.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration
public class CnpjRestClientConfig {

    private static final String CNPJ_API_BASE_URL = "https://api.cnpja.com";

    @Value("${cnpja.api.authorization:}")
    private String authorization;

    @Bean
    public RestClient cnpjRestClient(RestClient.Builder builder) {
        var restClientBuilder = builder
                .baseUrl(CNPJ_API_BASE_URL)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json");

        if (authorization != null && !authorization.isBlank()) {
            restClientBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, authorization);
        }

        return restClientBuilder.build();
    }
}
