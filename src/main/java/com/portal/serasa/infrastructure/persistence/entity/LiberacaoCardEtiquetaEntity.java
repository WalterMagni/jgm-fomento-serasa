package com.portal.serasa.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "liberacao_card_etiqueta")
@IdClass(LiberacaoCardEtiquetaEntity.Chave.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiberacaoCardEtiquetaEntity {

    @Id
    @Column(name = "card_id")
    private UUID cardId;

    @Id
    @Column(name = "etiqueta_id")
    private UUID etiquetaId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Chave implements Serializable {
        private UUID cardId;
        private UUID etiquetaId;
    }
}
