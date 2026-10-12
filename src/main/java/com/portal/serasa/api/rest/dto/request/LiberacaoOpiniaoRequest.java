package com.portal.serasa.api.rest.dto.request;

import com.portal.serasa.domain.model.liberacao.QuadroOpiniao;
import com.portal.serasa.domain.model.liberacao.ResultadoLiberacao;
import jakarta.validation.constraints.NotNull;

/**
 * Quadrado O ou C de um sacado.
 *
 * @param situacao nula limpa o quadrado
 */
public record LiberacaoOpiniaoRequest(@NotNull QuadroOpiniao quadro, ResultadoLiberacao situacao) {
}
