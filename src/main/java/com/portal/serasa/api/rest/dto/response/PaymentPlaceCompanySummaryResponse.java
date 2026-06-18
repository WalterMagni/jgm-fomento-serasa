package com.portal.serasa.api.rest.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resumo das decisões de praça de pagamento para uma empresa (na condição de cedente).
 * Visão Sacado = títulos do cedente decididos como SACADO; Visão Cedente = decididos como CEDENTE.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPlaceCompanySummaryResponse {

    private String documentNumber;
    private int sacadoCount;
    private BigDecimal sacadoValue;
    private int cedenteCount;
    private BigDecimal cedenteValue;
    private int totalCount;
    private BigDecimal totalValue;
    private List<PaymentPlaceEntryResponse> entries;
    private int page;
    private int size;
    private int totalPages;
    private int totalFilteredElements;
}
