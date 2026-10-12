package com.portal.serasa.api.rest.dto.response;

import com.portal.serasa.domain.model.prospeccao.CondicaoSugerida;
import com.portal.serasa.domain.model.prospeccao.RespostaCliente;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ProspeccaoAceiteResponse(
        UUID id,
        UUID decisaoId,
        RespostaCliente resposta,
        BigDecimal limiteSugerido,
        List<CondicaoSugerida> condicoesSugeridas,
        String mensagem,
        String registradoPorNome,
        boolean emNomeDoComercial,
        LocalDateTime registradoEm) {
}
