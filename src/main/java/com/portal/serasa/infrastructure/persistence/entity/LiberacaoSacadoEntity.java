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

    /** Decisão sobre este sacado. Nulo = a decidir. Ver V70. */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(length = 12)
    private com.portal.serasa.domain.model.liberacao.ResultadoLiberacao situacao;

    /** Só no parcial: quanto deste sacado foi aprovado. */
    @Column(name = "valor_aprovado", precision = 15, scale = 2)
    private BigDecimal valorAprovado;

    @Column(name = "situacao_por_nome", length = 200)
    private String situacaoPorNome;

    @Column(name = "situacao_em")
    private java.time.LocalDateTime situacaoEm;

    /** Linha do sacado na Análise de Risco (AR), quando o card veio do PDF. Ver V73. */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private com.portal.serasa.domain.model.liberacao.CarteiraSacado carteira;
}
