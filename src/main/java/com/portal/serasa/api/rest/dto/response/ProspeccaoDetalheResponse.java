package com.portal.serasa.api.rest.dto.response;

import com.portal.serasa.api.rest.dto.DadosComerciaisDto;
import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import lombok.Builder;

import java.util.List;

/** Card aberto no painel: identificação, checklist agrupado, timeline e para onde pode ir. */
@Builder
public record ProspeccaoDetalheResponse(
        ProspeccaoResponse card,
        DadosComerciaisDto dadosComerciais,
        List<ProspeccaoDocumentoResponse> documentos,
        List<ProspeccaoEventoResponse> timeline,
        /** Destinos válidos a partir do estágio atual — a tela não repete a máquina de estados. */
        List<EstagioProspeccao> destinosPossiveis,
        /** Todas as decisões, da mais nova para a mais antiga; a vigente vem marcada. */
        List<ProspeccaoDecisaoResponse> decisoes,
        List<ProspeccaoAceiteResponse> aceites,
        /** Quem está olhando pode decidir: é a analista dona ou admin. Calculado pela mesma regra que autoriza. */
        boolean podeDecidir) {
}
