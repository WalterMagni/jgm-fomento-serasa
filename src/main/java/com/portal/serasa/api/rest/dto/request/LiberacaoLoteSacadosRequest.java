package com.portal.serasa.api.rest.dto.request;

import com.portal.serasa.domain.model.liberacao.QuadroOpiniao;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Vários sacados de uma vez, no quadrado O ou C.
 *
 * @param situacao a opção (no O também Parcial e Amostragem; no C, Aprovado ou Reprovado); nula não muda a marcação
 * @param parecer  o parecer daquele quadrado para todos; nulo não muda, vazio apaga
 */
public record LiberacaoLoteSacadosRequest(
        @NotEmpty @Size(max = 200) List<String> documentos,
        @NotNull QuadroOpiniao quadro,
        @Size(max = 20) String situacao,
        @Size(max = 20000) String parecer) {
}
