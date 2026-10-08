package com.portal.serasa.infrastructure.persistence.entity;

import com.portal.serasa.domain.model.liberacao.CorLiberacao;
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

/** Etiqueta compartilhada da esteira. Ver V67. */
@Entity
@Table(name = "liberacao_etiqueta")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiberacaoEtiquetaEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, length = 40)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private CorLiberacao cor;

    @Column(name = "criado_por_nome", length = 200)
    private String criadoPorNome;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
