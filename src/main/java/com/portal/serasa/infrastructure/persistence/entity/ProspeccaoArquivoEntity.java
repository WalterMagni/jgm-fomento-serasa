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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Metadado do arquivo enviado. O byte vive no compartilhamento de rede que o backend já monta.
 * Ver V59.
 */
@Entity
@Table(name = "prospeccao_arquivo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProspeccaoArquivoEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "prospeccao_id", nullable = false)
    private UUID prospeccaoId;

    @Column(name = "documento_id")
    private UUID documentoId;

    /** Sempre relativo à base configurável em runtime; caminho absoluto nunca é gravado. */
    @Column(name = "caminho_relativo", nullable = false, columnDefinition = "text")
    private String caminhoRelativo;

    @Column(name = "nome_original", nullable = false, length = 300)
    private String nomeOriginal;

    @Column(name = "mime_type", nullable = false, length = 120)
    private String mimeType;

    @Column(name = "tamanho_bytes", nullable = false)
    private Long tamanhoBytes;

    @Column(nullable = false)
    @Builder.Default
    private Integer versao = 1;

    @Column(name = "enviado_por")
    private UUID enviadoPor;

    @Column(name = "enviado_em", nullable = false)
    private LocalDateTime enviadoEm;

    /** Remoção lógica: o registro sai da tela, o byte fica no compartilhamento. */
    @Column(name = "removido_em")
    private LocalDateTime removidoEm;

    @Column(name = "removido_por")
    private UUID removidoPor;
}
