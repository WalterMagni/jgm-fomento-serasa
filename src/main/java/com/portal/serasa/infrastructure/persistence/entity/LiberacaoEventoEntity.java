package com.portal.serasa.infrastructure.persistence.entity;

import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import com.portal.serasa.domain.model.liberacao.TipoEventoLiberacao;
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

/** Linha da timeline do card. Append-only. Ver V64. */
@Entity
@Table(name = "liberacao_evento")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiberacaoEventoEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoEventoLiberacao tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "etapa_de", length = 12)
    private EtapaLiberacao etapaDe;

    @Enumerated(EnumType.STRING)
    @Column(name = "etapa_para", length = 12)
    private EtapaLiberacao etapaPara;

    @Column(length = 40)
    private String campo;

    @Column(name = "valor_antes", columnDefinition = "text")
    private String valorAntes;

    @Column(name = "valor_depois", columnDefinition = "text")
    private String valorDepois;

    @Column(columnDefinition = "text")
    private String texto;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "usuario_nome", nullable = false, length = 200)
    private String usuarioNome;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
