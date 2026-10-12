package com.portal.serasa.api.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProdutoCatalogoRequest(@NotBlank @Size(max = 120) String nome, Integer ordem, Boolean ativo) {
}
