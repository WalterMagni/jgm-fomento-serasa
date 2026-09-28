package com.portal.serasa.api.rest.dto.response;

import com.portal.serasa.domain.model.prospeccao.CanalContato;
import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import com.portal.serasa.domain.model.prospeccao.TipoEventoProspeccao;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record ProspeccaoEventoResponse(
        UUID id,
        TipoEventoProspeccao tipo,
        CanalContato canal,
        EstagioProspeccao estagioDe,
        EstagioProspeccao estagioPara,
        String texto,
        /** Denormalizado: continua legível depois que o usuário sai da empresa. */
        String usuarioNome,
        LocalDateTime criadoEm) {
}
