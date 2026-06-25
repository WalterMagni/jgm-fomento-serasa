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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Padrão aprendido por par cedente × sacado. Agrega as decisões do analista entre
 * esses dois documentos; o {@link com.portal.serasa.application.service.PaymentPlaceScorer}
 * usa a contagem para reforçar a sugestão nas importações seguintes. Documentos são
 * guardados só com dígitos (payer costuma vir mascarado do PDF).
 */
@Entity
@Table(name = "payment_place_patterns")
@EntityListeners(org.springframework.data.jpa.domain.support.AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPlacePatternEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "client_document", nullable = false, length = 20)
    private String clientDocument;

    @Column(name = "payer_document", nullable = false, length = 20)
    private String payerDocument;

    @Column(name = "cedente_count", nullable = false)
    private int cedenteCount;

    @Column(name = "sacado_count", nullable = false)
    private int sacadoCount;

    @Column(name = "inconclusivo_count", nullable = false)
    private int inconclusivoCount;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "last_decision", length = 30)
    private String lastDecision;

    @Column(name = "last_decided_at")
    private LocalDateTime lastDecidedAt;

    @Column(name = "locked", nullable = false)
    private boolean locked;

    @Column(name = "locked_decision", length = 30)
    private String lockedDecision;

    @Column(name = "locked_by_name")
    private String lockedByName;

    @org.springframework.data.annotation.CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @org.springframework.data.annotation.LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
