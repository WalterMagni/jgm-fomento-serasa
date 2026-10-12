package com.portal.serasa.api.rest.dto.request;

import com.portal.serasa.domain.model.prospeccao.CondicaoSugerida;
import com.portal.serasa.domain.model.prospeccao.RespostaCliente;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Resposta do cliente. Contraproposta exige mensagem; limite e condições sugeridos são opcionais. */
public record ProspeccaoAceiteRequest(
        @NotNull RespostaCliente resposta,
        @Size(max = 8000) String mensagem,
        BigDecimal limiteSugerido,
        @Size(max = 30) List<CondicaoSugerida> condicoesSugeridas) {
}
