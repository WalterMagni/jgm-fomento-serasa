package com.portal.serasa.api.rest.dto.response;

import com.portal.serasa.domain.model.prospeccao.EscopoDocumento;
import lombok.Builder;

import java.util.UUID;

@Builder
public record DocumentoTipoResponse(
        UUID id,
        String codigo,
        String nome,
        EscopoDocumento escopo,
        boolean obrigatorio,
        boolean informativo,
        String somenteUf,
        boolean ativo,
        int ordem) {
}
