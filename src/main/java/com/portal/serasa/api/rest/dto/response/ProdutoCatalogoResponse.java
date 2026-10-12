package com.portal.serasa.api.rest.dto.response;

import java.util.UUID;

public record ProdutoCatalogoResponse(UUID id, String nome, int ordem, boolean ativo) {
}
