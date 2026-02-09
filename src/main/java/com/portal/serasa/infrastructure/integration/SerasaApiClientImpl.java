package com.portal.serasa.infrastructure.integration;

import com.portal.serasa.application.port.out.SerasaApiClient;
import com.portal.serasa.domain.model.CreditAnalysis;
import com.portal.serasa.domain.model.CreditAnalysisStatus;
import com.portal.serasa.infrastructure.integration.dto.SerasaResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Implementação do cliente HTTP para a API Serasa usando Spring RestClient.
 * Placeholder: endpoint e mapeamento serão ajustados conforme contrato real.
 */
@Component
@Slf4j
public class SerasaApiClientImpl implements SerasaApiClient {

    private final RestClient serasaRestClient;

    public SerasaApiClientImpl(@Qualifier("serasaRestClient") RestClient serasaRestClient) {
        this.serasaRestClient = serasaRestClient;
    }

    @Override
    public CreditAnalysis consultarCredito(String cnpj) {
        log.debug("Consultando crédito para CNPJ: {}", cnpj);

        // Placeholder: endpoint real será definido com o contrato da API Serasa
        // Por ora, retorna dados mock para permitir desenvolvimento
        try {
            SerasaResponseDto response = serasaRestClient.get()
                    .uri("/credit-analysis/{cnpj}", cnpj)
                    .retrieve()
                    .body(SerasaResponseDto.class);

            return mapToDomain(cnpj, response);
        } catch (Exception e) {
            log.warn("Falha ao consultar API Serasa (usando mock): {}", e.getMessage());
            return buildMockResponse(cnpj);
        }
    }

    private CreditAnalysis mapToDomain(String cnpj, SerasaResponseDto dto) {
        if (dto == null) {
            return buildMockResponse(cnpj);
        }
        return CreditAnalysis.builder()
                .cnpj(dto.getCnpj() != null ? dto.getCnpj() : cnpj)
                .score(dto.getScore())
                .consultaEm(LocalDateTime.now())
                .status(parseStatus(dto.getStatus()))
                .build();
    }

    private CreditAnalysis buildMockResponse(String cnpj) {
        return CreditAnalysis.builder()
                .cnpj(cnpj)
                .score(null)
                .consultaEm(LocalDateTime.now())
                .status(CreditAnalysisStatus.EM_ANALISE)
                .build();
    }

    private CreditAnalysisStatus parseStatus(String status) {
        if (status == null) {
            return CreditAnalysisStatus.EM_ANALISE;
        }
        return Optional.ofNullable(status)
                .map(String::toUpperCase)
                .map(s -> {
                    try {
                        return CreditAnalysisStatus.valueOf(s);
                    } catch (IllegalArgumentException e) {
                        return CreditAnalysisStatus.EM_ANALISE;
                    }
                })
                .orElse(CreditAnalysisStatus.EM_ANALISE);
    }
}
