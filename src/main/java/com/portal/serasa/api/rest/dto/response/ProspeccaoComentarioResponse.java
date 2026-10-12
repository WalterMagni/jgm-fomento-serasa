package com.portal.serasa.api.rest.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProspeccaoComentarioResponse(
        UUID id,
        UUID autorId,
        String autorNome,
        String texto,
        LocalDateTime criadoEm,
        LocalDateTime editadoEm) {
}
