package com.portal.serasa.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditAnalysis {

    private Long id;
    private UUID clientId;
    private String cnpj;
    private String companyName;
    private Integer score;
    private String riskClass;
    private BigDecimal probability;
    private LocalDateTime analysisDate;
    private JsonNode inquiryHistory;
    private JsonNode negativeSummary;
    private JsonNode partnerDetails;
    private JsonNode creditRatingDetails;
    private JsonNode paymentHistory;
    private JsonNode companyParticipationsReport;
    private String originalPayload;
    /** PENDENTE | SIM | NAO — detectado pela presença de dados no nó assignor da Serasa */
    private String visaoCedente;
    private LocalDateTime consultaEm;
    private CreditAnalysisStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String aiAnalysis;
    private LocalDateTime aiAnalysisDate;
}
