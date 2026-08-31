package com.portal.serasa.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sócio (pessoa física ou jurídica) consolidado a partir da Receita e do Serasa.
 * Ver V52 para a semântica de {@code documentMask} × {@code document}.
 */
@Entity
@Table(name = "shareholders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareholderEntity {

    public static final String SOURCE_RECEITA_MASK = "RECEITA_MASK";
    public static final String SOURCE_SERASA = "SERASA";

    public static final String TYPE_CPF = "CPF";
    public static final String TYPE_CNPJ = "CNPJ";

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "document_type", nullable = false, length = 12)
    private String documentType;

    /** Documento completo. NULL enquanto só se conhece a máscara da Receita. */
    @Column(length = 14)
    private String document;

    @Column(name = "document_mask", nullable = false, length = 14)
    private String documentMask;

    @Column(nullable = false, columnDefinition = "text")
    private String name;

    @Column(name = "document_source", nullable = false, length = 20)
    private String documentSource;

    @Column(name = "age_range", length = 1)
    private String ageRange;

    /** Restritivos da consulta paga de pessoa física (dívidas, protestos, pefin/refin). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "restrictive_data", columnDefinition = "jsonb")
    private String restrictiveData;

    @Column(name = "restrictive_score")
    private Integer restrictiveScore;

    @Column(name = "restrictive_fetched_at")
    private LocalDateTime restrictiveFetchedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** {@code true} quando o documento completo já é conhecido (upgrade do Serasa aplicado). */
    public boolean hasFullDocument() {
        return document != null && !document.isBlank();
    }
}
