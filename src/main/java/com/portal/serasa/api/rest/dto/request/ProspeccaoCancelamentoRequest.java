package com.portal.serasa.api.rest.dto.request;

import com.portal.serasa.domain.model.prospeccao.MotivoRecusa;
import jakarta.validation.constraints.Size;

/** Cancelar card aprovado. Motivo da lista da recusa; "Outro" exige observação, validado no serviço. */
public record ProspeccaoCancelamentoRequest(
        MotivoRecusa motivo,
        @Size(max = 4000) String observacao) {
}
