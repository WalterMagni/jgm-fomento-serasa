package com.portal.serasa.api.rest.dto.response;

import com.portal.serasa.domain.model.prospeccao.StatusPendencia;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ProspeccaoPendenciaResponse(
        UUID id,
        String pergunta,
        List<String> camposExigidos,
        List<UUID> documentosPedidos,
        StatusPendencia status,
        String abertaPorNome,
        LocalDateTime abertaEm,
        String resposta,
        String respondidaPorNome,
        boolean emNomeDoComercial,
        LocalDateTime fechadaEm) {
}
