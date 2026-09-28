package com.portal.serasa.infrastructure.persistence.entity;

import com.portal.serasa.domain.model.prospeccao.CanalContato;
import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import com.portal.serasa.domain.model.prospeccao.TipoEventoProspeccao;
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

/** Linha da timeline do card. Append-only: nunca editada nem apagada. Ver V57. */
@Entity
@Table(name = "prospeccao_evento")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProspeccaoEventoEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "prospeccao_id", nullable = false)
    private UUID prospeccaoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoEventoProspeccao tipo;

    @Enumerated(EnumType.STRING)
    @Column(length = 12)
    private CanalContato canal;

    @Enumerated(EnumType.STRING)
    @Column(name = "estagio_de", length = 24)
    private EstagioProspeccao estagioDe;

    @Enumerated(EnumType.STRING)
    @Column(name = "estagio_para", length = 24)
    private EstagioProspeccao estagioPara;

    @Column(columnDefinition = "text")
    private String texto;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    /** Denormalizado: a timeline continua legível depois que o usuário sai da empresa. */
    @Column(name = "usuario_nome", length = 200)
    private String usuarioNome;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
