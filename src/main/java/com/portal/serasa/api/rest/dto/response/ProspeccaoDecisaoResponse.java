package com.portal.serasa.api.rest.dto.response;

import com.portal.serasa.domain.model.prospeccao.MotivoRecusa;
import com.portal.serasa.domain.model.prospeccao.ResultadoDecisao;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ProspeccaoDecisaoResponse(
        UUID id,
        int versao,
        ResultadoDecisao resultado,
        BigDecimal limite,
        List<Condicao> condicoes,
        String parecer,
        /** Texto da aprovação com ressalva; nulo sem ressalva. */
        String ressalva,
        MotivoRecusa motivoRecusa,
        boolean vigente,
        String decididoPorNome,
        LocalDateTime decididoEm) {

    public record Condicao(String produto, BigDecimal valor) {
    }
}
