package com.portal.serasa.infrastructure.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.portal.serasa.domain.model.PoloJudit;
import com.portal.serasa.domain.model.ServicoJudit;
import com.portal.serasa.domain.model.SituacaoConsultaJudit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Uma chamada cobrada pela Judit, com a resposta crua. Cache da tela e extrato do mês. Ver V89, V91 e V99. */
@Entity
@Table(name = "judit_consulta")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JuditConsultaEntity {

    @Id
    @UuidGenerator
    private UUID id;

    /** CNPJ ou CPF consultado. */
    @Column(nullable = false, length = 14)
    private String documento;

    /** Empresa habilitada que autorizou a consulta. Igual ao documento quando ele é a própria empresa. */
    @Column(name = "cnpj_empresa", nullable = false, length = 14)
    private String cnpjEmpresa;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ServicoJudit servico;

    /**
     * Estimativa pela tabela do contrato. A fatura da Judit é a palavra final. No On Demand nasce igual
     * a {@link #custoEstimado} e vira o custo pelo total devolvido quando a resposta chega.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal custo;

    /** O que a tela prometeu antes de cobrar. Só no On Demand. */
    @Column(name = "custo_estimado", precision = 10, scale = 2)
    private BigDecimal custoEstimado;

    /** O polo pedido. Só no On Demand. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 7)
    private PoloJudit polo;

    /** A pré-consulta (RESUMO) usada na estimativa do On Demand. Nula: a pessoa pulou a pré-consulta. */
    @Column(name = "pre_consulta_id")
    private UUID preConsultaId;

    /** Para o painel de consumo e o aviso no sino. Nulo quando a tela não mandou e não há cadastro. */
    @Column(name = "nome_empresa")
    private String nomeEmpresa;

    @Column(name = "total_processos")
    private Integer totalProcessos;

    @Column(name = "request_id", length = 100)
    private String requestId;

    /** Número CNJ do processo, no detalhe. Nulo nas consultas por documento. */
    @Column(length = 25)
    private String cnj;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 12)
    @Builder.Default
    private SituacaoConsultaJudit situacao = SituacaoConsultaJudit.CONCLUIDA;

    /** Nula enquanto o detalhe está pendente. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode resposta;

    @Column(name = "concluida_em")
    private LocalDateTime concluidaEm;

    @Column(columnDefinition = "text")
    private String erro;

    /** Última vez que o acompanhamento perguntou a situação à Judit. Nulo: ainda não perguntou. */
    @Column(name = "verificado_em")
    private LocalDateTime verificadoEm;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "usuario_nome")
    private String usuarioNome;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
