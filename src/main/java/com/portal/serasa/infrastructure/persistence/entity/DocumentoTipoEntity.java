package com.portal.serasa.infrastructure.persistence.entity;

import com.portal.serasa.domain.model.prospeccao.EscopoDocumento;
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
 * Catálogo de documentos exigidos, editável pelo admin. Ver V58.
 *
 * <p>O card copia nome e obrigatoriedade ao materializar o checklist, então mexer aqui muda o
 * que passa a ser exigido dali em diante, sem reescrever o que já foi cobrado.</p>
 */
@Entity
@Table(name = "documento_tipo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentoTipoEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, length = 60, unique = true)
    private String codigo;

    @Column(nullable = false, columnDefinition = "text")
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private EscopoDocumento escopo;

    @Column(nullable = false)
    @Builder.Default
    private Boolean obrigatorio = true;

    /** Item que é pergunta, não documento — aparece, aceita resposta, nunca trava. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean informativo = false;

    /**
     * Obrigatório, mas liberável mediante justificativa.
     *
     * <p>Endividamento e curva ABC o cliente pode legitimamente não ter; a autorização SCR ele
     * pode não aceitar assinar. Nesses casos o time libera com motivo registrado, em vez de
     * simplesmente ignorar a exigência.</p>
     */
    @Column(name = "admite_excecao", nullable = false)
    @Builder.Default
    private Boolean admiteExcecao = false;

    /**
     * UF em que o item existe. A certidão simplificada é retirada na JUCESP, ou seja, só São
     * Paulo; fora dela o item nasce NAO_APLICAVEL.
     */
    @Column(name = "somente_uf", length = 2)
    private String somenteUf;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    @Column(nullable = false)
    private Integer ordem;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
