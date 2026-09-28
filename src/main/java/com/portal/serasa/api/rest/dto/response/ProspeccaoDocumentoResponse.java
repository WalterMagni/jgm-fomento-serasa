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
        EscopoDocumento escopo,
        String socioNome,
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
