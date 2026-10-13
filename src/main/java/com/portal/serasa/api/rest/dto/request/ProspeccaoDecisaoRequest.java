package com.portal.serasa.api.rest.dto.request;

import com.portal.serasa.domain.model.prospeccao.MotivoRecusa;
import com.portal.serasa.domain.model.prospeccao.ResultadoDecisao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Decisão da análise. Aprovação leva limite e condições; reprovação leva motivo. As regras que
 * dependem do resultado ficam no serviço — aqui só o formato.
 */
public record ProspeccaoDecisaoRequest(
        @NotNull ResultadoDecisao resultado,
        BigDecimal limite,
        @Valid @Size(max = 30) List<CondicaoRequest> condicoes,
        @Size(max = 8000) String parecer,
        MotivoRecusa motivoRecusa,
        @Size(max = 4000) String observacao,
        /** Aprovação com ressalva: o texto da analista. Sem ele, aprovação comum. */
        @Size(max = 4000) String ressalva) {

    public record CondicaoRequest(@Size(max = 120) String produto, BigDecimal valor) {
    }
}
