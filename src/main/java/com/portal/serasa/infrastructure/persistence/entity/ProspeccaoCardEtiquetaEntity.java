package com.portal.serasa.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/** Etiqueta marcada num card. Ver V78. */
@Entity
@Table(name = "prospeccao_card_etiqueta")
@IdClass(ProspeccaoCardEtiquetaEntity.Chave.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProspeccaoCardEtiquetaEntity {

    @Id
    @Column(name = "prospeccao_id")
    private UUID prospeccaoId;

    @Id
    @Column(name = "etiqueta_id")
    private UUID etiquetaId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Chave implements Serializable {
        private UUID prospeccaoId;
        private UUID etiquetaId;
    }
}
