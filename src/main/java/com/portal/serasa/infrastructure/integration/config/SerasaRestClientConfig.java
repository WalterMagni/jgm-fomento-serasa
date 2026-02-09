package com.portal.serasa.infrastructure.integration.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class SerasaRestClientConfig {

    @Value("${serasa.api.base-url}")
    private String baseUrl;

    @Bean
    public RestClient serasaRestClient(RestClient.Builder builder) {
        return builder
                .baseUrl(baseUrl)
                .build();
    }
}
