package com.portal.serasa.api.rest.dto.request;

import com.portal.serasa.domain.model.prospeccao.PapelPessoa;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Pessoa adicionada à mão ao checklist.
 *
 * <p>Serve principalmente para o avalista: ele entrega os mesmos documentos do sócio, mas não
 * consta do quadro societário e por isso não há de onde puxá-lo.</p>
 */
public record ProspeccaoPessoaRequest(
        @NotBlank @Size(max = 200) String nome,
        @Size(max = 14) String documento,
        @NotNull PapelPessoa papel) {
}
