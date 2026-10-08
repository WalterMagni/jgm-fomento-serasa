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

/** Anexo do card. O byte mora no compartilhamento; aqui só o metadado. Ver V71. */
@Entity
@Table(name = "liberacao_anexo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiberacaoAnexoEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "caminho_relativo", nullable = false, columnDefinition = "text")
    private String caminhoRelativo;

    @Column(name = "nome_original", nullable = false, length = 255)
    private String nomeOriginal;

    @Column(name = "mime_type", length = 120)
    private String mimeType;

    @Column(name = "tamanho_bytes", nullable = false)
    private Long tamanhoBytes;

    @Column(name = "enviado_por_id")
    private UUID enviadoPorId;

    @Column(name = "enviado_por_nome", nullable = false, length = 200)
    private String enviadoPorNome;

    @Column(name = "enviado_em", nullable = false)
    private LocalDateTime enviadoEm;

    @Column(name = "removido_em")
    private LocalDateTime removidoEm;

    @Column(name = "removido_por_nome", length = 200)
    private String removidoPorNome;
}
