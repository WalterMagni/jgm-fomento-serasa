package com.portal.serasa.infrastructure.persistence.entity;

import com.portal.serasa.domain.model.liberacao.OrigemMembro;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "liberacao_membro")
@IdClass(LiberacaoMembroEntity.Chave.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiberacaoMembroEntity {

    @Id
    @Column(name = "card_id")
    private UUID cardId;

    @Id
    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private OrigemMembro origem;

    @Column(name = "adicionado_em", nullable = false)
    private LocalDateTime adicionadoEm;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Chave implements Serializable {
        private UUID cardId;
        private UUID usuarioId;
    }
}
