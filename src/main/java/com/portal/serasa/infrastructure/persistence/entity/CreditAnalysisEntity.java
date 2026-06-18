package com.portal.serasa.infrastructure.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.portal.serasa.domain.model.CreditAnalysisStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "credit_analysis")
@EntityListeners(org.springframework.data.jpa.domain.support.AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditAnalysisEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id")
    private UUID clientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", insertable = false, updatable = false)
    private com.portal.serasa.infrastructure.persistence.entity.ClientEntity client;

    @Column(nullable = false, length = 14)
    private String cnpj;

    @Column(name = "company_name", length = 500)
    private String companyName;

    @Column
    private Integer score;

    @Column(name = "risk_class", length = 10)
    private String riskClass;

    @Column(precision = 10, scale = 4)
    private BigDecimal probability;

    @Column(name = "analysis_date")
    private LocalDateTime analysisDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "inquiry_history", columnDefinition = "jsonb")
    private JsonNode inquiryHistory;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "negative_summary", columnDefinition = "jsonb")
    private JsonNode negativeSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "partner_details", columnDefinition = "jsonb")
    private JsonNode partnerDetails;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "credit_rating_details", columnDefinition = "jsonb")
    private JsonNode creditRatingDetails;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payment_history", columnDefinition = "jsonb")
    private JsonNode paymentHistory;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "company_participations_report", columnDefinition = "jsonb")
    private JsonNode companyParticipationsReport;

    @Column(name = "original_payload", columnDefinition = "text")
    private String originalPayload;

    @Builder.Default
    @Column(name = "visao_cedente", nullable = false, length = 10)
    private String visaoCedente = "PENDENTE";

    @Column(name = "consulta_em", nullable = false)
    private LocalDateTime consultaEm;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private CreditAnalysisStatus status;

    @org.springframework.data.annotation.CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @org.springframework.data.annotation.LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "ai_analysis", columnDefinition = "TEXT")
    private String aiAnalysis;

    @Column(name = "ai_analysis_date")
    private LocalDateTime aiAnalysisDate;
}
