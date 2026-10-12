package com.portal.serasa.api.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Texto livre: comentário ou resposta de pendência. */
public record ProspeccaoTextoRequest(@NotBlank @Size(max = 10_000) String texto) {
}
