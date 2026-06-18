package com.portal.serasa.infrastructure.integration.gemini;

import lombok.Builder;

import java.util.List;

/** Resultado da análise de praça de pagamento pelo Gemini (justificativa em linguagem natural). */
@Builder
public record PaymentPlaceAiResult(
        boolean available,
        String error,
        String suggestion,
        String confidence,
        String summary,
        List<String> factorsFor,
        List<String> factorsAgainst,
        String recommendation
) {
    public static PaymentPlaceAiResult unavailable(String error) {
        return PaymentPlaceAiResult.builder().available(false).error(error).build();
    }
}
