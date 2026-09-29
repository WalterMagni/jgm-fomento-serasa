package com.portal.serasa.api.rest.dto.response;

import com.portal.serasa.domain.model.prospeccao.EscopoDocumento;
import com.portal.serasa.domain.model.prospeccao.StatusDocumento;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record ProspeccaoDocumentoResponse(
        UUID id,
        String codigo,
        String nome,
        boolean obrigatorio,
        boolean informativo,
        /** Obrigatório liberável com justificativa: muda o texto da ação na tela. */
        boolean admiteExcecao,
        EscopoDocumento escopo,
        /** SOCIO ou AVALISTA. */
        String pessoaPapel,
        String socioNome,
        /** Participação no capital, quando conhecida. Só o Serasa informa. */
        java.math.BigDecimal socioParticipacao,
        String socioDocumento,
        boolean socioAtivo,
        String socioInativoMotivo,
        StatusDocumento status,
        String observacao,
        String motivo,
        LocalDateTime recebidoEm,
        LocalDateTime validadoEm,
        List<ProspeccaoArquivoResponse> arquivos) {
}
