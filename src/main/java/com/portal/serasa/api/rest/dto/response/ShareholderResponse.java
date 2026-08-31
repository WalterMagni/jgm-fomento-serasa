package com.portal.serasa.api.rest.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Sócio da empresa consultada. */
@Builder
public record ShareholderResponse(
        String name,
        /** CPF/CNPJ completo, quando conhecido. Nulo quando só temos a máscara da Receita. */
        String document,
        /** CPF mascarado como a Receita publica ({@code ***216508**}). */
        String documentMask,
        String documentType,
        /** RECEITA_MASK ou SERASA — indica se o documento completo já foi obtido. */
        String documentSource,
        String qualification,
        LocalDate entryDate,
        BigDecimal capitalPercent,
        String ageRange,
        /** Quantidade de outras empresas em que este sócio figura. */
        int otherCompaniesCount) {
}
