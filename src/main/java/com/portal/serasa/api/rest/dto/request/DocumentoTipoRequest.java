package com.portal.serasa.api.rest.dto.request;

import com.portal.serasa.domain.model.prospeccao.EscopoDocumento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Item do catálogo de documentos, mantido pelo admin. */
public record DocumentoTipoRequest(
        @NotBlank @Size(max = 60) String codigo,
        @NotBlank String nome,
        @NotNull EscopoDocumento escopo,
        boolean obrigatorio,
        /** Pergunta em vez de documento: aparece, aceita resposta, nunca trava o avanço. */
        boolean informativo,
        /** UF em que o item existe, como a certidão da JUCESP só existe em SP. */
        @Size(min = 2, max = 2) String somenteUf,
        boolean ativo,
        int ordem) {
}
