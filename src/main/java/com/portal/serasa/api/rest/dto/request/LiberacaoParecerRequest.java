package com.portal.serasa.api.rest.dto.request;

import com.portal.serasa.domain.model.liberacao.PosicaoParecer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LiberacaoParecerRequest(
        @NotNull PosicaoParecer posicao,
        @Size(max = 20000) String texto) {
}
