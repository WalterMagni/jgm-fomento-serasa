package com.portal.serasa.api.rest.dto.response;

import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import com.portal.serasa.domain.model.prospeccao.MotivoRecusa;
import com.portal.serasa.domain.model.prospeccao.OrigemProspeccao;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Card na listagem do kanban.
 *
 * <p>O semáforo de SLA vem calculado do backend ({@code slaEstourado}, {@code slaEmAtencao}) para
 * que a tela não repita a regra de dias úteis e feriado — duas contas diferentes divergiriam.</p>
 */
@Builder
public record ProspeccaoResponse(
        UUID id,
        String cnpj,
        String razaoSocial,
        EstagioProspeccao estagio,
        OrigemProspeccao origem,
        UUID comercialId,
        String comercialNome,
        UUID analistaId,
        String analistaNome,
        LocalDateTime estagioDesde,
        Integer prazoEstagioHoras,
        long horasNoEstagio,
        boolean slaEstourado,
        boolean slaEmAtencao,
        /** Trinta dias corridos sem retorno do cliente. */
        boolean silencioEmAtencao,
        /** Quarenta e cinco dias: o time encaminha para inerte. */
        boolean silencioProlongado,
        long diasEmSilencio,
        MotivoRecusa motivoRecusa,
        String observacao,
        Integer reaberturas,
        LocalDateTime ultimoContatoEm,
        /** Progresso do checklist, no formato que o card mostra: 8 de 13. */
        int documentosResolvidos,
        int documentosTotal,
        LocalDateTime createdAt,
        LocalDateTime closedAt) {
}
