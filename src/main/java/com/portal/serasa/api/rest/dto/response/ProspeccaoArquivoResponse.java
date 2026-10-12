package com.portal.serasa.api.rest.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

/** Metadado do arquivo. O caminho no compartilhamento nunca é exposto ao cliente. */
@Builder
public record ProspeccaoArquivoResponse(
        /** Onde o arquivo está, como a pessoa acha no Explorer. Ver {@code app.documentos.caminho-rede}. */
        String caminho,
        UUID id,
        UUID documentoId,
        String nomeOriginal,
        String mimeType,
        Long tamanhoBytes,
        Integer versao,
        LocalDateTime enviadoEm) {
}
