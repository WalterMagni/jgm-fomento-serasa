package com.portal.serasa.api.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Tira ou devolve um sócio ao checklist — sócio que saiu da sociedade ou faleceu. */
public record ProspeccaoSocioAtivoRequest(
        @NotBlank String socioNome,
        @NotNull Boolean ativo,
        String motivo) {
}
