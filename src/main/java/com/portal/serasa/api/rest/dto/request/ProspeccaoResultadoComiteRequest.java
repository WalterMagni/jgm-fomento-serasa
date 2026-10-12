package com.portal.serasa.api.rest.dto.request;

import com.portal.serasa.domain.model.prospeccao.MotivoRecusa;
import com.portal.serasa.domain.model.prospeccao.ResultadoComite;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Resultado do comitê. Reprovar exige motivo — conferido na troca de estágio. */
public record ProspeccaoResultadoComiteRequest(
        @NotNull ResultadoComite resultado,
        MotivoRecusa motivoRecusa,
        @Size(max = 4000) String observacao) {
}
