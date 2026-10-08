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

/** Comentário do card. Ver V65. */
@Entity
@Table(name = "liberacao_comentario")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiberacaoComentarioEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "autor_id")
    private UUID autorId;

    @Column(name = "autor_nome", nullable = false, length = 200)
    private String autorNome;

    @Column(nullable = false, columnDefinition = "text")
    private String texto;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "editado_em")
    private LocalDateTime editadoEm;

    @Column(name = "excluido_em")
    private LocalDateTime excluidoEm;
}
