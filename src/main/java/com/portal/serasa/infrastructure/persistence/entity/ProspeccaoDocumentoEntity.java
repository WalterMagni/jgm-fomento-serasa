package com.portal.serasa.infrastructure.persistence.entity;

import com.portal.serasa.domain.model.prospeccao.EscopoDocumento;
import com.portal.serasa.domain.model.prospeccao.StatusDocumento;
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

/** Item do checklist de um card, já materializado a partir do catálogo. Ver V58. */
@Entity
@Table(name = "prospeccao_documento")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProspeccaoDocumentoEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "prospeccao_id", nullable = false)
    private UUID prospeccaoId;

    /** Fica nulo se o tipo for removido do catálogo; o snapshot preserva o que foi exigido. */
    @Column(name = "documento_tipo_id")
    private UUID documentoTipoId;

    @Column(name = "codigo_snapshot", nullable = false, length = 60)
    private String codigoSnapshot;

    @Column(name = "nome_snapshot", nullable = false, columnDefinition = "text")
    private String nomeSnapshot;

    @Column(name = "obrigatorio_snapshot", nullable = false)
    private Boolean obrigatorioSnapshot;

    @Column(name = "informativo_snapshot", nullable = false)
    @Builder.Default
    private Boolean informativoSnapshot = false;

    /** Obrigatório que pode ser liberado com justificativa. Ver V60. */
    @Column(name = "admite_excecao_snapshot", nullable = false)
    @Builder.Default
    private Boolean admiteExcecaoSnapshot = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private EscopoDocumento escopo;

    @Column(name = "socio_nome", length = 200)
    private String socioNome;

    @Column(name = "socio_documento", length = 14)
    private String socioDocumento;

    /**
     * Sócio tem ciclo de vida: a planilha registra "vai sair da sociedade" e "Faleceu —
     * recebemos certidão de óbito". Inativo sai da conta de obrigatórios sem sumir da tela.
     */
    @Column(name = "socio_ativo", nullable = false)
    @Builder.Default
    private Boolean socioAtivo = true;

    @Column(name = "socio_inativo_motivo", columnDefinition = "text")
    private String socioInativoMotivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private StatusDocumento status = StatusDocumento.PENDENTE;

    /** É a observação que carrega o dado real: "Válida até 2032", "7 Clientes". */
    @Column(columnDefinition = "text")
    private String observacao;

    /** Exigido em REJEITADO e DISPENSADO. */
    @Column(columnDefinition = "text")
    private String motivo;

    @Column(name = "recebido_em")
    private LocalDateTime recebidoEm;

    @Column(name = "recebido_por")
    private UUID recebidoPor;

    @Column(name = "validado_em")
    private LocalDateTime validadoEm;

    @Column(name = "validado_por")
    private UUID validadoPor;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    /** Trava o avanço para DOCS_COMPLETOS enquanto verdadeiro. */
    public boolean bloqueiaFechamento() {
        return Boolean.TRUE.equals(obrigatorioSnapshot)
                && !Boolean.TRUE.equals(informativoSnapshot)
                && Boolean.TRUE.equals(socioAtivo)
                && !status.resolvido();
    }
}
