package com.portal.serasa.api.rest.dto.request;

import com.portal.serasa.domain.model.liberacao.QuadroOpiniao;
import com.portal.serasa.domain.model.liberacao.ResultadoLiberacao;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Decisão de um sacado pela lista do card: C (Comitê) ou D (Diretoria).
 *
 * @param situacao      nula limpa o quadrado
 * @param valorAprovado só no parcial
 */
public record LiberacaoDecisaoSacadoRequest(@NotNull QuadroOpiniao quadro, ResultadoLiberacao situacao,
                                            @DecimalMin("0.00") BigDecimal valorAprovado) {
}
