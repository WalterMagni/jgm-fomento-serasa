package com.portal.serasa.infrastructure.persistence.entity;

import com.portal.serasa.domain.model.CreditAnalysisStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "credit_analysis")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditAnalysisEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 14)
    private String cnpj;

    @Column
    private Integer score;

    @Column(name = "consulta_em", nullable = false)
    private LocalDateTime consultaEm;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private CreditAnalysisStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
