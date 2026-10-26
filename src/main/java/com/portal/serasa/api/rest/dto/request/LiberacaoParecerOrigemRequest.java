package com.portal.serasa.api.rest.dto.request;

import com.portal.serasa.domain.model.liberacao.PosicaoParecer;
import jakarta.validation.constraints.Size;

/**
 * Edição só do parecer da origem, em qualquer etapa.
 *
 * @param version a versão do card que a tela tinha; recusa se alguém salvou no meio
 */
public record LiberacaoParecerOrigemRequest(
        @Size(max = 20000) String parecerOrigem,
        PosicaoParecer posicaoOrigem,
        Long version) {
}
