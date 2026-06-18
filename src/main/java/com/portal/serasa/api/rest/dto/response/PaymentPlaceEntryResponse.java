package com.portal.serasa.api.rest.dto.response;

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
public class PaymentPlaceEntryResponse {

    private UUID id;
    private UUID batchId;
    private String section;
    private String externalId;
    private String clientCode;
    private String titleNumber;
    private String dueDate;
    private String titleValue;
    private String paidValue;
    private String occurrence;
    private String payerDocument;
    private String payerName;
    private String clientCity;
    private String agencyCityPdf;
    private String payerCity;
    private String bankAgency;
    private String bankCode;
    private String agencyCode;
    private String occurrenceComplement;
    private String analysisStatus;
    private String analystDecision;
    private String analystNotes;
    private String decidedByName;
    private LocalDateTime decidedAt;
    private String bankName;
    private String bacenAgencyName;
    private String bacenInstitutionName;
    private String bacenInstitutionType;
    private String institutionCategory;
    private String geographicReliability;
    private String geographicReliabilityReason;
    private String automaticSuggestion;
    private String automaticConfidence;
    private String automaticEvidence;
    private Integer scoreSacado;
    private Integer scoreCedente;
    private Object aiAnalysis;
    private LocalDateTime aiAnalyzedAt;
    private String bacenAgencyCity;
    private String bacenAgencyAddress;
    private String bacenAgencyZipCode;
    private BigDecimal distanceClientAgencyKm;
    private BigDecimal distanceAgencyPayerKm;
    private BigDecimal distanceClientPayerKm;
    private BigDecimal clientLatitude;
    private BigDecimal clientLongitude;
    private BigDecimal agencyLatitude;
    private BigDecimal agencyLongitude;
    private BigDecimal payerLatitude;
    private BigDecimal payerLongitude;
    private String clientAddress;
    private String clientName;
    private String clientDocument;
    private String payerAddress;
    private String agencyAddressResolved;
    private LocalDateTime agencyEnrichedAt;
    private LocalDateTime reopenedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
