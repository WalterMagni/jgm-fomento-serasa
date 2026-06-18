package com.portal.serasa.api.rest.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPlaceBatchIndicatorsResponse {

    private UUID batchId;
    private String fileName;
    private int totalEntries;
    private int locatedAgencyCount;
    private BigDecimal locatedAgencyPct;
    private int lowReliabilityCount;
    private BigDecimal lowReliabilityPct;
    private int comparableDecisionCount;
    private int agreementCount;
    private BigDecimal agreementPct;
    private int disagreementCount;
    private BigDecimal disagreementPct;
    private List<BankAgencyIndicatorResponse> topRecurringBankAgencies;
    private List<BankAgencyIndicatorResponse> topDivergentBankAgencies;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BankAgencyIndicatorResponse {
        private String bankAgency;
        private String bankName;
        private String bankCode;
        private String agencyCode;
        private int totalEntries;
        private int decidedEntries;
        private int agreementCount;
        private int disagreementCount;
        private BigDecimal disagreementPct;
    }
}
