package com.portal.serasa.api.rest.dto.request;

import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * O cliente declinou a operação.
 *
 * @param de     etapa em que a tela viu o card: se outra pessoa já o moveu, a recusa diz para onde
 * @param motivo por que o cliente declinou; opcional
 */
public record LiberacaoDeclinioRequest(@NotNull EtapaLiberacao de, @Size(max = 5000) String motivo) {
}
