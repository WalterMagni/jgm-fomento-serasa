package com.portal.serasa.api.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Pedido ao comercial: o texto e, opcionalmente, campos comerciais exigidos e documentos do checklist. */
public record ProspeccaoPendenciaRequest(
        @NotBlank @Size(max = 8000) String pergunta,
        @Size(max = 13) List<String> camposExigidos,
        @Size(max = 100) List<UUID> documentosPedidos) {
}
