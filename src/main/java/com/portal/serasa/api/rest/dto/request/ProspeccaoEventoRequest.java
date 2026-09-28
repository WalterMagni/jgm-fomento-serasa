package com.portal.serasa.api.rest.dto.request;

import com.portal.serasa.domain.model.prospeccao.CanalContato;
import jakarta.validation.constraints.NotBlank;

/** Cobrança ou nota na timeline. Canal nulo significa nota interna, sem contato com o cliente. */
public record ProspeccaoEventoRequest(
        CanalContato canal,
        @NotBlank String texto) {
}
