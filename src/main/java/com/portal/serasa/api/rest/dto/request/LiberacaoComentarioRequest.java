package com.portal.serasa.api.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LiberacaoComentarioRequest(@NotBlank @Size(max = 10000) String texto) {
}
