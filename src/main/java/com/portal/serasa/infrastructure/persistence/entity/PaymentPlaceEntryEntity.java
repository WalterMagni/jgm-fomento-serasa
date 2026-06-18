package com.portal.serasa.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_place_entries")
@EntityListeners(org.springframework.data.jpa.domain.support.AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPlaceEntryEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(nullable = false, length = 80)
    private String section;

    @Column(name = "external_id", nullable = false, length = 20)
    private String externalId;

    @Column(name = "client_code", length = 20)
    private String clientCode;

    @Column(name = "title_number", length = 60)
    private String titleNumber;

    @Column(name = "due_date", length = 10)
    private String dueDate;

    @Column(name = "title_value", length = 30)
    private String titleValue;

    @Column(name = "paid_value", length = 30)
    private String paidValue;

    private String occurrence;

    @Column(name = "payer_document", length = 30)
    private String payerDocument;

    @Column(name = "payer_name")
    private String payerName;

    @Column(name = "client_city")
    private String clientCity;

    @Column(name = "agency_city_pdf")
    private String agencyCityPdf;

    @Column(name = "payer_city")
    private String payerCity;

    @Column(name = "bank_agency", length = 20)
    private String bankAgency;

    @Column(name = "bank_code", length = 10)
    private String bankCode;

    @Column(name = "agency_code", length = 20)
    private String agencyCode;

    @Column(name = "occurrence_complement")
    private String occurrenceComplement;

    @Column(name = "analysis_status", nullable = false, length = 60)
    private String analysisStatus;

    @Column(name = "analyst_decision", length = 30)
    private String analystDecision;

    @Column(name = "analyst_notes", columnDefinition = "text")
    private String analystNotes;

    @Column(name = "decided_by_user_id")
    private UUID decidedByUserId;

    @Column(name = "decided_by_name")
    private String decidedByName;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bacen_agency_name")
    private String bacenAgencyName;

    @Column(name = "bacen_institution_name")
    private String bacenInstitutionName;

    @Column(name = "bacen_institution_type")
    private String bacenInstitutionType;

    @Column(name = "institution_category", length = 40)
    private String institutionCategory;

    @Column(name = "geographic_reliability", length = 40)
    private String geographicReliability;

    @Column(name = "geographic_reliability_reason", columnDefinition = "text")
    private String geographicReliabilityReason;

    @Column(name = "automatic_suggestion", length = 40)
    private String automaticSuggestion;

    @Column(name = "automatic_confidence", length = 40)
    private String automaticConfidence;

    @Column(name = "automatic_evidence", columnDefinition = "text")
    private String automaticEvidence;

    @Column(name = "score_sacado")
    private Integer scoreSacado;

    @Column(name = "score_cedente")
    private Integer scoreCedente;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "ai_analysis", columnDefinition = "jsonb")
    private String aiAnalysis;

    @Column(name = "ai_analyzed_at")
    private LocalDateTime aiAnalyzedAt;

    @Column(name = "bacen_agency_city")
    private String bacenAgencyCity;

    @Column(name = "bacen_agency_address", columnDefinition = "text")
    private String bacenAgencyAddress;

    @Column(name = "bacen_agency_zip_code", length = 20)
    private String bacenAgencyZipCode;

    @Column(name = "distance_client_agency_km")
    private BigDecimal distanceClientAgencyKm;

    @Column(name = "distance_agency_payer_km")
    private BigDecimal distanceAgencyPayerKm;

    @Column(name = "distance_client_payer_km")
    private BigDecimal distanceClientPayerKm;

    @Column(name = "client_latitude")
    private BigDecimal clientLatitude;

    @Column(name = "client_longitude")
    private BigDecimal clientLongitude;

    @Column(name = "agency_latitude")
    private BigDecimal agencyLatitude;

    @Column(name = "agency_longitude")
    private BigDecimal agencyLongitude;

    @Column(name = "payer_latitude")
    private BigDecimal payerLatitude;

    @Column(name = "payer_longitude")
    private BigDecimal payerLongitude;

    @Column(name = "client_address", columnDefinition = "text")
    private String clientAddress;

    @Column(name = "client_name", length = 500)
    private String clientName;

    @Column(name = "client_document", length = 14)
    private String clientDocument;

    @Column(name = "payer_address", columnDefinition = "text")
    private String payerAddress;

    @Column(name = "agency_address_resolved", columnDefinition = "text")
    private String agencyAddressResolved;

    @Column(name = "agency_enriched_at")
    private LocalDateTime agencyEnrichedAt;

    @Column(name = "reopened_at")
    private LocalDateTime reopenedAt;

    @org.springframework.data.annotation.CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @org.springframework.data.annotation.LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
