package com.portal.serasa.infrastructure.integration.cnpja;

import com.portal.serasa.infrastructure.integration.cnpja.dto.CompanyDetailDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class CnpjApiClientImpl implements CnpjApiClient {

    private final RestClient cnpjRestClient;

    public CnpjApiClientImpl(@Qualifier("cnpjRestClient") RestClient cnpjRestClient) {
        this.cnpjRestClient = cnpjRestClient;
    }

    @Override
    public CompanyDetailDto consultarCnpj(String cnpj) {
        String documentNumber = cnpj.replaceAll("\\D", "");
        if (documentNumber.length() != 14) {
            throw new IllegalArgumentException("CNPJ deve conter 14 dígitos");
        }

        log.debug("Consultando CNPJ Já para: {}", documentNumber);

        return cnpjRestClient.get()
                .uri("/office/{cnpj}?geocoding=true", documentNumber)
                .retrieve()
                .body(CompanyDetailDto.class);
    }
}
