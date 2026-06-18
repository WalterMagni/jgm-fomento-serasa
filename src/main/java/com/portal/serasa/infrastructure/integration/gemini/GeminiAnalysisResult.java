package com.portal.serasa.infrastructure.integration.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiAnalysisResult {

    private boolean available;
    private String parecer;
    private String visaoCedente;
    private String nivelRisco;
    private String recomendacao;
    private String[] pontosFortes;
    private String[] pontosAtencao;
    private String errorMessage;

    public static GeminiAnalysisResult unavailable(String reason) {
        return GeminiAnalysisResult.builder()
                .available(false)
                .errorMessage(reason)
                .build();
    }
}
