package com.portal.serasa.api.rest.dto.request;

import com.portal.serasa.domain.model.liberacao.QuadroOpiniao;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Parecer em texto de um quadrado do sacado (O, C ou D).
 *
 * @param texto vazio apaga; aceita @menção
 */
public record LiberacaoParecerSacadoRequest(@NotNull QuadroOpiniao quadro, @Size(max = 20000) String texto) {
}
