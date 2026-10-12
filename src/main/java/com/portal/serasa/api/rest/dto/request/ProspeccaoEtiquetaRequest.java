package com.portal.serasa.api.rest.dto.request;

import com.portal.serasa.domain.model.liberacao.CorLiberacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProspeccaoEtiquetaRequest(@NotBlank @Size(max = 40) String nome, CorLiberacao cor) {
}
