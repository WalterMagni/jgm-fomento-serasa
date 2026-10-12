package com.portal.serasa.api.rest.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

/** Metadado do arquivo. O caminho no compartilhamento nunca é exposto ao cliente. */
@Builder
public record ProspeccaoArquivoResponse(
        UUID id,
        UUID documentoId,
        String nomeOriginal,
        String mimeType,
        Long tamanhoBytes,
        Integer versao,
        LocalDateTime enviadoEm) {
}
