package com.portal.serasa.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Valor a mais no limite da Judit, liberado pelo admin, que vale só no mês indicado. Ver V99. */
@Entity
@Table(name = "judit_liberacao")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JuditLiberacaoEntity {

    @Id
    @UuidGenerator
    private UUID id;

    /** Sempre o dia 1 do mês. */
    @Column(nullable = false)
    private LocalDate mes;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(columnDefinition = "text")
    private String motivo;

    @Column(name = "admin_id")
    private UUID adminId;

    @Column(name = "admin_nome")
    private String adminNome;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
