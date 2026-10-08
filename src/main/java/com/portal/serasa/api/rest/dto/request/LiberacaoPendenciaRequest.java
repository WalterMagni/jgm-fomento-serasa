package com.portal.serasa.api.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record LiberacaoPendenciaRequest(
        @NotNull UUID destinatarioId,
        @NotBlank @Size(max = 5000) String texto) {
}
