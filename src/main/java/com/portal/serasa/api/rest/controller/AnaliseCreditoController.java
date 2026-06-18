package com.portal.serasa.api.rest.controller;

import com.portal.serasa.application.service.CompanyDetailService;
import com.portal.serasa.application.service.CreditAnalysisService;
import com.portal.serasa.application.service.credit.MotorCreditoService;
import com.portal.serasa.domain.model.CompanyDetail;
import com.portal.serasa.domain.model.CreditAnalysis;
import com.portal.serasa.domain.model.credit.PerfilCredito;
import com.portal.serasa.domain.model.credit.ResultadoAnaliseCredito;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/credit-analysis")
@RequiredArgsConstructor
@Tag(name = "Motor de Crédito", description = "Motor de Política de Crédito - Classificação automática")
public class AnaliseCreditoController {

    private final MotorCreditoService motorCreditoService;
    private final CreditAnalysisService creditAnalysisService;
    private final CompanyDetailService companyDetailService;

    @Operation(summary = "Analisar crédito por CNPJ", description = "Executa a política de crédito baseada na análise mais recente")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Análise realizada"),
            @ApiResponse(responseCode = "404", description = "Sem dados de análise para este CNPJ")
    })
    @GetMapping("/{cnpj}/decision")
    public ResponseEntity<ResultadoAnaliseCredito> analisarCredito(@PathVariable String cnpj) {

        List<CreditAnalysis> analyses = creditAnalysisService.buscarPorCnpj(cnpj);
        if (analyses.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Pega a análise mais recente (assumindo que a query ordena ou o último é o
        // mais atual)
        CreditAnalysis analysis = analyses.get(analyses.size() - 1);

        // Busca os detalhes da empresa para o extrator (se existirem)
        Optional<CompanyDetail> companyOpt = companyDetailService.findByDocumentNumber(cnpj);

        ResultadoAnaliseCredito resultado = motorCreditoService.analisar(analysis, companyOpt);
        return ResponseEntity.ok(resultado);
    }

    @Operation(summary = "Resumo rápido da decisão", description = "Retorna apenas perfil e score")
    @GetMapping("/{cnpj}/decision/summary")
    public ResponseEntity<Map<String, Object>> resumoAnalise(@PathVariable String cnpj) {

        List<CreditAnalysis> analyses = creditAnalysisService.buscarPorCnpj(cnpj);
        if (analyses.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        CreditAnalysis analysis = analyses.get(analyses.size() - 1);
        Optional<CompanyDetail> companyOpt = companyDetailService.findByDocumentNumber(cnpj);

        ResultadoAnaliseCredito resultado = motorCreditoService.analisar(analysis, companyOpt);

        Map<String, Object> resumo = new HashMap<>();
        resumo.put("cnpj", resultado.getCnpj());
        resumo.put("razaoSocial", resultado.getRazaoSocial());
        resumo.put("perfil", resultado.getPerfil().name());
        resumo.put("decisao", resultado.getDecisao());
        resumo.put("titulo", resultado.getTitulo());
        resumo.put("corHex", resultado.getCorHex());
        resumo.put("scoreCredito", resultado.getScoreCredito());
        resumo.put("aprovado", resultado.isAprovado());
        resumo.put("precisaAnaliseManual", resultado.precisaAnaliseManual());

        return ResponseEntity.ok(resumo);
    }

    @GetMapping("/profiles")
    public ResponseEntity<List<Map<String, Object>>> listarPerfis() {
        List<Map<String, Object>> perfis = Arrays.stream(PerfilCredito.values())
                .map(p -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("codigo", p.name());
                    map.put("decisao", p.getDecisao());
                    map.put("titulo", p.getTitulo());
                    map.put("corHex", p.getCorHex());
                    return map;
                }).toList();

        return ResponseEntity.ok(perfis);
    }
}
