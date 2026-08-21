package com.portal.serasa.infrastructure.integration.cnpja;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.serasa.application.service.ApiRequestCounterService;
import com.portal.serasa.domain.exception.ExternalApiException;
import com.portal.serasa.infrastructure.integration.cnpja.dto.CnpjApiResponse;
import com.portal.serasa.infrastructure.integration.cnpja.dto.CompanyDetailDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@Slf4j
public class CnpjApiClientImpl implements CnpjApiClient {

    public static final String PROVIDER = "CNPJ Já";

    private final RestClient cnpjRestClient;
    private final ObjectMapper objectMapper;
    private final ApiRequestCounterService apiRequestCounterService;

    public CnpjApiClientImpl(@Qualifier("cnpjRestClient") RestClient cnpjRestClient,
                             ObjectMapper objectMapper,
                             ApiRequestCounterService apiRequestCounterService) {
        this.cnpjRestClient = cnpjRestClient;
        this.objectMapper = objectMapper;
        this.apiRequestCounterService = apiRequestCounterService;
    }

    @Override
    public CnpjApiResponse consultarCnpj(String cnpj) {
        String documentNumber = cnpj.replaceAll("\\D", "");
        if (documentNumber.length() != 14) {
            throw new IllegalArgumentException("CNPJ deve conter 14 dígitos");
        }

        log.debug("Consultando CNPJ Já para: {}", documentNumber);

        String rawJson;
        try {
            rawJson = cnpjRestClient.get()
                    .uri("/office/{cnpj}?geocoding=true", documentNumber)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw new ExternalApiException(PROVIDER, e.getStatusCode().value(),
                    describeUpstreamError(e.getStatusCode().value()), e);
        } catch (ResourceAccessException e) {
            // Timeout, DNS, conexão recusada — nem chegou a haver resposta.
            throw new ExternalApiException(PROVIDER, 0,
                    "A API CNPJ Já não respondeu (tempo esgotado). Tente novamente ou use o Serasa.", e);
        }

        try {
            CompanyDetailDto dto = objectMapper.readValue(rawJson, CompanyDetailDto.class);
            apiRequestCounterService.increment(ApiRequestCounterService.PROVIDER_CNPJA);
            return new CnpjApiResponse(dto, rawJson);
        } catch (JsonProcessingException e) {
            throw new ExternalApiException(PROVIDER, 0,
                    "A API CNPJ Já devolveu uma resposta inesperada.", e);
        }
    }

    /** Traduz o status do provedor para uma mensagem que faça sentido para o analista. */
    private static String describeUpstreamError(int status) {
        return switch (status) {
            case 400 -> "CNPJ inválido para a API CNPJ Já.";
            case 401, 403 -> "Chave da API CNPJ Já inválida ou sem permissão. Avise o suporte.";
            case 404 -> "CNPJ não encontrado na base do CNPJ Já.";
            case 429 -> "Limite de consultas do CNPJ Já atingido. Tente novamente em alguns minutos.";
            default -> status >= 500
                    ? "A API CNPJ Já está fora do ar no momento (erro " + status + ")."
                    : "Falha na consulta ao CNPJ Já (erro " + status + ").";
        };
    }
}
