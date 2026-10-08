package com.portal.serasa.api.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LiberacaoRespostaRequest(@NotBlank @Size(max = 10000) String resposta) {
}
