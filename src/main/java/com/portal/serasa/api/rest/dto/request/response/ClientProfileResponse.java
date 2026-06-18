package com.portal.serasa.api.rest.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.List;

/**
 * Perfil completo do cliente: dados CNPJ Já + Serasa (Credit Rating).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientProfileResponse {

    private ClientResponse client;
    private CompanyDetailResponse companyDetail;
    private CreditAnalysisResponse creditAnalysis;
    private List<CreditAnalysisHistoryItemResponse> analysisHistory;

    /**
     * Análises PF disponíveis para os sócios desta empresa.
     * Chave = CPF (11 dígitos). Apenas sócios com consulta salva aparecem.
     */
    private Map<String, PersonAnalysisSummaryResponse> partnerPfAnalyses;
}
