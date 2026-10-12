package com.portal.serasa.api.rest.dto.response;

import com.portal.serasa.domain.model.liberacao.PosicaoParecer;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProspeccaoParecerResponse(
        UUID id,
        int rodada,
        UUID usuarioId,
        String usuarioNome,
        PosicaoParecer posicao,
        String texto,
        LocalDateTime registradoEm) {
}
