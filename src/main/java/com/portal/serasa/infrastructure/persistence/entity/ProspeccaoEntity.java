package com.portal.serasa.infrastructure.persistence.entity;

import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import com.portal.serasa.domain.model.prospeccao.MotivoRecusa;
import com.portal.serasa.domain.model.prospeccao.OrigemProspeccao;
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
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Card da esteira de prospecção. Ver V56.
 *
 * <p>Diferente das entidades mais antigas do projeto, que guardam enums como String solta, aqui
 * os estados são {@code @Enumerated(STRING)}: o card é uma máquina de estados, e deixar o
 * compilador cobrar o conjunto de valores vale mais do que a uniformidade com o código
 * anterior.</p>
 */
@Entity
@Table(name = "prospeccao")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProspeccaoEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, length = 14)
    private String cnpj;

    @Column(name = "razao_social", nullable = false, columnDefinition = "text")
    private String razaoSocial;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EstagioProspeccao estagio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    @Builder.Default
    private OrigemProspeccao origem = OrigemProspeccao.MANUAL;

    /** Análise que originou o card, quando veio de {@code visaoCedente = SIM}. */
    @Column(name = "credit_analysis_id")
    private Long creditAnalysisId;

    /**
     * O comercial pode não ter login: a planilha usa "DIRETO" para o que entra sem
     * intermediário. Por isso o id é opcional e o nome é sempre gravado.
     */
    @Column(name = "comercial_id")
    private UUID comercialId;

    @Column(name = "comercial_nome", length = 200)
    private String comercialNome;

    @Column(name = "analista_id")
    private UUID analistaId;

    @Column(name = "estagio_desde", nullable = false)
    private LocalDateTime estagioDesde;

    /** Prazo do estágio em horas úteis, congelado na transição. Ver V56 e V61. */
    @Column(name = "prazo_estagio_horas", nullable = false)
    private Integer prazoEstagioHoras;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_recusa", length = 40)
    private MotivoRecusa motivoRecusa;

    @Column(columnDefinition = "text")
    private String observacao;

    @Column(nullable = false)
    @Builder.Default
    private Integer reaberturas = 0;

    /** Zerado por qualquer cobrança. É o relógio dos 30 dias de silêncio. */
    @Column(name = "ultimo_contato_em")
    private LocalDateTime ultimoContatoEm;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;
}
