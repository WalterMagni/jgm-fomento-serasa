package com.portal.serasa.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Limite atual da empresa como sacado, uma linha por CNPJ. Ver V102. */
@Entity
@Table(name = "empresa_limite_sacado")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmpresaLimiteSacadoEntity {

    @Id
    @Column(length = 14)
    private String cnpj;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal valor;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "usuario_nome")
    private String usuarioNome;

    @Column(name = "definido_em", nullable = false)
    private LocalDateTime definidoEm;
}
