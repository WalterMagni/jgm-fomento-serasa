package com.portal.serasa.infrastructure.integration.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration
public class BacenRestClientConfig {

    private static final String BACEN_OLINDA_BASE_URL =
            "https://olinda.bcb.gov.br/olinda/servico/Informes_Agencias/versao/v1/odata";

    @Bean
    public RestClient bacenRestClient(RestClient.Builder builder) {
        return builder
                .baseUrl(BACEN_OLINDA_BASE_URL)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }
}
