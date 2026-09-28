package com.portal.serasa.api.rest.dto.request;

import com.portal.serasa.domain.model.prospeccao.StatusDocumento;
import jakarta.validation.constraints.NotNull;

/** Conferência de um item do checklist. */
public record ProspeccaoDocumentoStatusRequest(
        @NotNull StatusDocumento status,
        /** O dado real do documento: "CRC válido", "7 clientes", "válida até 2032". */
        String observacao,
        /** Exigido em REJEITADO e DISPENSADO. */
        String motivo) {
}
