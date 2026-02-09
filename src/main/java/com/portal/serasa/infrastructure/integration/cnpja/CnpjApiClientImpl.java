package com.portal.serasa.infrastructure.integration.cnpja;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.serasa.application.service.ApiRequestCounterService;
import com.portal.serasa.infrastructure.integration.cnpja.dto.CnpjApiResponse;
import com.portal.serasa.infrastructure.integration.cnpja.dto.CompanyDetailDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class CnpjApiClientImpl implements CnpjApiClient {

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

        String rawJson = cnpjRestClient.get()
                .uri("/office/{cnpj}?geocoding=true", documentNumber)
                .retrieve()
                .body(String.class);

        try {
            CompanyDetailDto dto = objectMapper.readValue(rawJson, CompanyDetailDto.class);
            apiRequestCounterService.increment(ApiRequestCounterService.PROVIDER_CNPJA);
            return new CnpjApiResponse(dto, rawJson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao parsear resposta da API CNPJ Já", e);
        }
    }
}
