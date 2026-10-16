package com.portal.serasa.infrastructure.integration.config;

import com.portal.serasa.infrastructure.integration.judit.JuditProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Slf4j
@Configuration
@EnableConfigurationProperties(JuditProperties.class)
public class JuditRestClientConfig {

    /** Rotas síncronas (base histórica): resumo e lista. */
    @Bean
    public RestClient juditRestClient(RestClient.Builder builder, JuditProperties properties) {
        return montar(builder, properties.lawsuitsUrl(), properties);
    }

    /** Rotas assíncronas (pedido + acompanhamento): detalhe de processo por CNJ. */
    @Bean
    public RestClient juditRequestsRestClient(RestClient.Builder builder, JuditProperties properties) {
        return montar(builder, properties.requestsUrl(), properties);
    }

    private static RestClient montar(RestClient.Builder builder, String baseUrl, JuditProperties properties) {
        // A rota síncrona responde em milissegundos, mas a lista de uma empresa grande passa de 2 MB.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        var restClientBuilder = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (properties.configurada()) {
            restClientBuilder.defaultHeader("api-key", properties.apiKey());
        } else {
            log.warn("JUDIT_API_KEY não configurada. A consulta de processos fica indisponível ({}).", baseUrl);
        }
        return restClientBuilder.build();
    }
}
