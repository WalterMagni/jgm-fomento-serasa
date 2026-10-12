package com.portal.serasa.api.rest.dto.request;

import jakarta.validation.constraints.Size;

/** Quem assina o contrato pelo cliente. */
public record ProspeccaoContratoRequest(
        @Size(max = 200) String nome,
        @Size(max = 40) String telefone,
        @Size(max = 200) String email) {
}
