package com.portal.serasa.api.rest.dto.request;

import com.portal.serasa.domain.model.liberacao.OpiniaoOperador;
import com.portal.serasa.domain.model.liberacao.QuadroOpiniao;
import jakarta.validation.constraints.NotNull;

/**
 * Quadrado O de um sacado: a opinião dos operadores (inclui Amostragem).
 *
 * @param situacao nula limpa o quadrado
 */
public record LiberacaoOpiniaoRequest(@NotNull QuadroOpiniao quadro, OpiniaoOperador situacao) {
}
