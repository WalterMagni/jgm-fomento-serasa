package com.portal.serasa.api.rest.dto.response;

import lombok.Builder;

import java.util.Map;

/** Contadores do topo do kanban. */
@Builder
public record ProspeccaoResumoResponse(
        Map<String, Long> porEstagio,
        long total,
        long slaEstourado,
        long slaEmAtencao,
        long silencioProlongado,
        /**
         * Cards que exigem ação hoje, sem dupla contagem: atraso de prazo e silêncio do cliente
         * costumam cair no mesmo card, e somar os dois inflaria o badge do menu.
         */
        long precisamAtencao) {
}
