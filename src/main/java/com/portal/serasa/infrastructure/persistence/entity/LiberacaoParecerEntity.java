package com.portal.serasa.infrastructure.persistence.entity;

import com.portal.serasa.domain.model.liberacao.PosicaoParecer;
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

/** Parecer de um membro do Comitê numa rodada. {@code posicao} nula = ainda aguardando. */
@Entity
@Table(name = "liberacao_parecer")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiberacaoParecerEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(nullable = false)
    private Integer rodada;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "usuario_nome", nullable = false, length = 200)
    private String usuarioNome;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private PosicaoParecer posicao;

    @Column(columnDefinition = "text")
    private String texto;

    @Column(name = "registrado_em")
    private LocalDateTime registradoEm;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    public boolean registrado() {
        return posicao != null;
    }
}
