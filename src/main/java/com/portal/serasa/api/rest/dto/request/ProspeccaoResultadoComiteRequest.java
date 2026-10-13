package com.portal.serasa.api.rest.dto.request;

import com.portal.serasa.domain.model.prospeccao.MotivoRecusa;
import com.portal.serasa.domain.model.prospeccao.ResultadoComite;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado do comitê. Reprovar exige motivo — conferido na troca de estágio. Aprovar pode trazer
 * limite e condições ajustados; sem eles vale a proposta da análise.
 */
public record ProspeccaoResultadoComiteRequest(
        @NotNull ResultadoComite resultado,
        MotivoRecusa motivoRecusa,
        @Size(max = 4000) String observacao,
        BigDecimal limite,
        @Valid @Size(max = 30) List<ProspeccaoDecisaoRequest.CondicaoRequest> condicoes) {
}
