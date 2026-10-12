package com.portal.serasa.api.rest.dto.response;

import com.portal.serasa.domain.model.liberacao.CorLiberacao;

import java.util.UUID;

public record ProspeccaoEtiquetaResponse(UUID id, String nome, CorLiberacao cor) {
}
