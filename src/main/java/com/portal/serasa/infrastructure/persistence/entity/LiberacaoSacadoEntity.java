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

@Entity
@Table(name = "liberacao_sacado")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiberacaoSacadoEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(nullable = false, length = 14)
    private String cnpj;

    @Column(columnDefinition = "text")
    private String nome;

    @Column(precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private Integer ordem;
}
