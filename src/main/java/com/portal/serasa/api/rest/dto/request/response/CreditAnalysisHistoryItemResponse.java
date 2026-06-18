package com.portal.serasa.api.rest.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditAnalysisHistoryItemResponse {
    private Long id;
    private String companyName;
    private String status;
    private String visaoCedente;
    private String riskClass;
    private LocalDateTime consultaEm;
    private LocalDateTime createdAt;
}
