package com.portal.serasa.api.rest.dto.request;

import com.portal.serasa.domain.model.liberacao.PosicaoParecer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProspeccaoParecerRequest(@NotNull PosicaoParecer posicao, @Size(max = 8000) String texto) {
}
