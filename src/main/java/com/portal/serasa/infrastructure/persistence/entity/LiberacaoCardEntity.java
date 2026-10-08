package com.portal.serasa.infrastructure.persistence.entity;

import com.portal.serasa.domain.model.liberacao.CorLiberacao;
import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import com.portal.serasa.domain.model.liberacao.TipoOperacao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Card da esteira de liberação de operações. Ver V64. */
@Entity
@Table(name = "liberacao_card")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiberacaoCardEntity {

    @Id
    @UuidGenerator
    private UUID id;

    /** Identidade gerada pelo banco; lida de volta depois do insert. */
    @Generated(event = EventType.INSERT)
    @Column(insertable = false, updatable = false)
    private Long numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EtapaLiberacao etapa;

    @Column(name = "etapa_desde", nullable = false)
    private LocalDateTime etapaDesde;

    @Column(nullable = false)
    @Builder.Default
    private Integer rodada = 1;

    @Column(name = "cedente_cnpj", nullable = false, length = 14)
    private String cedenteCnpj;

    @Column(name = "cedente_nome", nullable = false, columnDefinition = "text")
    private String cedenteNome;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_operacao", length = 16)
    private TipoOperacao tipoOperacao;

    @Column(precision = 15, scale = 2)
    private BigDecimal valor;

    private LocalDateTime prazo;

    @Column(name = "parecer_origem", columnDefinition = "text")
    private String parecerOrigem;

    /**
     * Faixa no topo do card. Gravada por consulta própria, fora da trava de versão: trocar a cor
     * não pode derrubar com 409 quem está editando os campos ao mesmo tempo. Ver V67.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 12, insertable = false, updatable = false)
    private CorLiberacao cor;

    @Column(name = "criado_por_id")
    private UUID criadoPorId;

    @Column(name = "criado_por_nome", nullable = false, length = 200)
    private String criadoPorNome;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_por_id")
    private UUID atualizadoPorId;

    @Column(name = "atualizado_por_nome", nullable = false, length = 200)
    private String atualizadoPorNome;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @Column(name = "finalizado_em")
    private LocalDateTime finalizadoEm;

    @Column(name = "excluido_em")
    private LocalDateTime excluidoEm;

    @Column(name = "excluido_por_id")
    private UUID excluidoPorId;

    @Column(name = "excluido_por_nome", length = 200)
    private String excluidoPorNome;

    @Version
    private Long version;
}
