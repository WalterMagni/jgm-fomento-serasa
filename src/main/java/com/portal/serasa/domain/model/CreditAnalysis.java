package com.portal.serasa.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditAnalysis {

    private Long id;
    private String cnpj;
    private Integer score;
    private LocalDateTime consultaEm;
    private CreditAnalysisStatus status;
}
