package com.portal.serasa.api.rest.controller;

import com.portal.serasa.api.rest.dto.request.VisaoCedenteAiReportRequest;
import com.portal.serasa.api.rest.dto.response.CreditAnalysisResponse;
import com.portal.serasa.api.rest.mapper.CreditAnalysisDtoMapper;
import com.portal.serasa.application.port.out.CreditAnalysisRepository;
import com.portal.serasa.infrastructure.integration.gemini.GeminiAiService;
import com.portal.serasa.infrastructure.integration.gemini.GeminiPortfolioReportResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Endpoints de B.I. para prospecção comercial: Visão Cedente.
 * <p>
 * GET /api/v1/reports/visao-cedente         → lista empresas com visaoCedente=SIM
 * GET /api/v1/reports/visao-cedente/summary → contadores por status
 */
@RestController
@RequestMapping("/api/v1/reports/visao-cedente")
@RequiredArgsConstructor
public class VisaoCedenteController {

    private final CreditAnalysisRepository creditAnalysisRepository;
    private final CreditAnalysisDtoMapper dtoMapper;
    private final GeminiAiService geminiAiService;

    /**
     * Retorna a análise mais recente de cada CNPJ onde visaoCedente = SIM.
     * Utilizado pelo BI de prospecção comercial no frontend.
     */
    @GetMapping
    public ResponseEntity<List<CreditAnalysisResponse>> listarCedentes() {
        List<CreditAnalysisResponse> result = creditAnalysisRepository
                .findLatestByVisaoCedente("SIM")
                .stream()
                .map(dtoMapper::toResponse)
                .toList();
        return ResponseEntity.ok(result);
    }

    /**
     * Retorna os contadores por status de Visão Cedente para uso nos cards do dashboard.
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Long>> summary() {
        long sim = creditAnalysisRepository.countByVisaoCedente("SIM");
        long nao = creditAnalysisRepository.countByVisaoCedente("NAO");
        long pendente = creditAnalysisRepository.countByVisaoCedente("PENDENTE");
        return ResponseEntity.ok(Map.of("SIM", sim, "NAO", nao, "PENDENTE", pendente));
    }

    @PostMapping("/ai-report")
    public ResponseEntity<GeminiPortfolioReportResult> generateAiReport(
            @Valid @RequestBody VisaoCedenteAiReportRequest request) {
        GeminiPortfolioReportResult result = geminiAiService.generateVisaoCedenteReport(
                request.prompt(),
                request.context()
        );
        return ResponseEntity.ok(result);
    }
}
