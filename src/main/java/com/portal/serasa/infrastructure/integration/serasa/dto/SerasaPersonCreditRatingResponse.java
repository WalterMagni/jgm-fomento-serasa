package com.portal.serasa.infrastructure.integration.serasa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * DTO para o relatório RELATORIO_AVANCADO_PF da API Serasa Experian.
 * Endpoint: /credit-services/person-information-report/v1/creditreport
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SerasaPersonCreditRatingResponse(
        List<Report> reports
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Report(
            String reportName,
            Map<String, Object> registration,
            Map<String, Object> negativeData,
            Map<String, Object> negativeSummary,
            Map<String, Object> facts,
            Map<String, Object> partner,
            Map<String, Object> checkFilingsHistorical
    ) {}
}
