package com.portal.serasa.infrastructure.integration.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP para o geocodificador Nominatim (OpenStreetMap). A política de uso
 * do serviço público exige um User-Agent identificável e no máximo 1 req/s
 * (throttle aplicado em {@code AddressGeocoder}). Sem key, sem custo.
 */
@Configuration
public class NominatimRestClientConfig {

    private static final String NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org";

    @Value("${nominatim.user-agent:portal-serasa-fomento/1.0 (contato: ti@jgmfomento.com.br)}")
    private String userAgent;

    @Bean
    public RestClient nominatimRestClient(RestClient.Builder builder) {
        return builder
                .baseUrl(NOMINATIM_BASE_URL)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .build();
    }
}
