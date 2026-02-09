package com.portal.serasa.api.rest.controller;

import com.portal.serasa.api.rest.dto.response.CreditAnalysisResponse;
import com.portal.serasa.api.rest.mapper.CreditAnalysisDtoMapper;
import com.portal.serasa.application.port.in.CreditAnalysisUseCase;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.domain.model.CreditAnalysis;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/credit-analysis")
@RequiredArgsConstructor
@Validated
public class CreditAnalysisController {

    private final CreditAnalysisUseCase creditAnalysisUseCase;
    private final CreditAnalysisDtoMapper dtoMapper;

    @PostMapping("/consultar/{cnpj}")
    public ResponseEntity<CreditAnalysisResponse> consultarESalvar(
            @PathVariable
            @NotBlank
            @Size(min = 14, max = 14)
            @Pattern(regexp = "\\d{14}", message = "CNPJ deve conter 14 dígitos numéricos")
            String cnpj) {
        CreditAnalysis analysis = creditAnalysisUseCase.consultarESalvar(cnpj);
        return ResponseEntity.status(HttpStatus.CREATED).body(dtoMapper.toResponse(analysis));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CreditAnalysisResponse> buscarPorId(@PathVariable Long id) {
        CreditAnalysis analysis = creditAnalysisUseCase.buscarPorId(id)
                .orElseThrow(() -> new EntityNotFoundException("Análise de crédito não encontrada com id: " + id));
        return ResponseEntity.ok(dtoMapper.toResponse(analysis));
    }

    @GetMapping("/cnpj/{cnpj}")
    public ResponseEntity<List<CreditAnalysisResponse>> buscarPorCnpj(
            @PathVariable
            @NotBlank
            @Size(min = 14, max = 14)
            @Pattern(regexp = "\\d{14}", message = "CNPJ deve conter 14 dígitos numéricos")
            String cnpj) {
        List<CreditAnalysisResponse> responses = creditAnalysisUseCase.buscarPorCnpj(cnpj).stream()
                .map(dtoMapper::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }
}
