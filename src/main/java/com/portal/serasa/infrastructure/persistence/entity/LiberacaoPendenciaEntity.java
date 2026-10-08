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

@Entity
@Table(name = "liberacao_pendencia")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiberacaoPendenciaEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "aberta_por_id")
    private UUID abertaPorId;

    @Column(name = "aberta_por_nome", nullable = false, length = 200)
    private String abertaPorNome;

    @Column(name = "destinatario_id")
    private UUID destinatarioId;

    @Column(name = "destinatario_nome", nullable = false, length = 200)
    private String destinatarioNome;

    @Column(nullable = false, columnDefinition = "text")
    private String texto;

    @Column(columnDefinition = "text")
    private String resposta;

    @Column(name = "aberta_em", nullable = false)
    private LocalDateTime abertaEm;

    @Column(name = "respondida_em")
    private LocalDateTime respondidaEm;

    @Column(name = "respondida_por_nome", length = 200)
    private String respondidaPorNome;

    public boolean aberta() {
        return respondidaEm == null;
    }
}
