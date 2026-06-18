package com.portal.serasa.api.rest.dto.request;

import jakarta.validation.constraints.NotBlank;

public record VisaoCedenteAiReportRequest(
        @NotBlank(message = "Prompt é obrigatório.")
        String prompt,
        @NotBlank(message = "Contexto é obrigatório.")
        String context
) {
}
