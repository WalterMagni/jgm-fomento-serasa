package com.portal.serasa.api.rest.dto.request;

import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import com.portal.serasa.domain.model.prospeccao.MotivoRecusa;
import jakarta.validation.constraints.NotNull;

/** Troca de estágio. Motivo é exigido nos terminais negativos, e validado no serviço. */
public record ProspeccaoTransicaoRequest(
        @NotNull EstagioProspeccao estagio,
        MotivoRecusa motivoRecusa,
        String observacao) {
}
