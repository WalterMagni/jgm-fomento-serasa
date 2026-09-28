package com.portal.serasa.api.rest.dto.response;

import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import lombok.Builder;

import java.util.List;

/** Card aberto no painel: identificação, checklist agrupado, timeline e para onde pode ir. */
@Builder
public record ProspeccaoDetalheResponse(
        ProspeccaoResponse card,
        List<ProspeccaoDocumentoResponse> documentos,
        List<ProspeccaoEventoResponse> timeline,
        /** Destinos válidos a partir do estágio atual — a tela não repete a máquina de estados. */
        List<EstagioProspeccao> destinosPossiveis) {
}
