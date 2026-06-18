package com.portal.serasa.infrastructure.integration.gemini;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GeminiPortfolioReportResult {
    private boolean available;
    private String title;
    private String summary;
    private String[] highlights;
    private String[] recommendations;
    private String errorMessage;

    public static GeminiPortfolioReportResult unavailable(String reason) {
        return GeminiPortfolioReportResult.builder()
                .available(false)
                .errorMessage(reason)
                .highlights(new String[0])
                .recommendations(new String[0])
                .build();
    }
}
