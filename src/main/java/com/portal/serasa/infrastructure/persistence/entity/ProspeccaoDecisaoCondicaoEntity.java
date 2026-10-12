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
import java.util.UUID;

/** Produto e valor de uma decisão aprovada. Ver V75. */
@Entity
@Table(name = "prospeccao_decisao_condicao")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProspeccaoDecisaoCondicaoEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "decisao_id", nullable = false)
    private UUID decisaoId;

    @Column(nullable = false, length = 120)
    private String produto;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private Integer ordem;
}
