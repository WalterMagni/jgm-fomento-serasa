package com.portal.serasa.infrastructure.integration.serasa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * DTO para o relatório RELATORIO_AVANCADO_PJ_ANALITICO com optionalFeatures=QSA_AVANCADO.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SerasaCreditRatingResponse(
        List<Report> reports,
        OptionalFeatures optionalFeatures
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Report(
            String reportName,
            Map<String, Object> identificationReport,
            Map<String, Object> negativeData,
            Map<String, Object> facts,
            Map<String, Object> negativeSummary,
            Map<String, Object> advancedCommercialPaymentHistory,
            Map<String, Object> checkFilingsHistorical
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OptionalFeatures(
            @JsonProperty("QSAReport") QSAReport qsaReport
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QSAReport(
            Map<String, Object> companyData,
            Map<String, Object> partnerCompleteReport,
            Map<String, Object> directorCompleteReport
    ) {}
}
