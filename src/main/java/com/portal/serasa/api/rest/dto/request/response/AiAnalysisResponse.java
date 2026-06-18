package com.portal.serasa.api.rest.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiAnalysisResponse {

    private boolean available;
    private String parecer;
    private String visaoCedente;
    private String nivelRisco;
    private String recomendacao;
    private String[] pontosFortes;
    private String[] pontosAtencao;
    private String errorMessage;
    private java.time.LocalDateTime analysisDate;
}
