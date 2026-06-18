package com.portal.serasa.api.rest.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Resumo da análise de PF — retornado dentro do perfil de empresa
 * para cada sócio que já possui consulta salva.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonAnalysisSummaryResponse {
    private String cpf;
    private String personName;
    private LocalDateTime consultaEm;
    private boolean hasNegative;
    private int negativeTotalCount;
}
