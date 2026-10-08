package com.portal.serasa.infrastructure.persistence.entity;

import com.portal.serasa.domain.model.notificacao.TipoNotificacao;
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

/** Ver V66. */
@Entity
@Table(name = "notificacao")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificacaoEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "destinatario_id", nullable = false)
    private UUID destinatarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoNotificacao tipo;

    @Column(nullable = false, length = 300)
    private String titulo;

    @Column(columnDefinition = "text")
    private String resumo;

    @Column(nullable = false, length = 500)
    private String link;

    @Column(name = "ator_id")
    private UUID atorId;

    @Column(name = "ator_nome", length = 200)
    private String atorNome;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm;

    @Column(name = "lida_em")
    private LocalDateTime lidaEm;
}
