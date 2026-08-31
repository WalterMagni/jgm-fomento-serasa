package com.portal.serasa.api.rest.controller;

import com.portal.serasa.api.rest.dto.response.RelatedCompanyResponse;
import com.portal.serasa.api.rest.dto.response.ShareholderResponse;
import com.portal.serasa.application.service.ShareholderBackfillService;
import com.portal.serasa.application.service.ShareholderViewService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Quadro societário e grupo econômico a partir da cópia local do cadastro da Receita.
 *
 * <p>Responde 503 quando a base da Receita está indisponível, mesmo tratamento das filiais:
 * é uma fonte auxiliar, não pode derrubar a página da empresa.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/company")
@RequiredArgsConstructor
@Validated
public class CompanyShareholderController {

    private static final String CNPJ_PATTERN = "^[0-9.\\-/]+$";

    private final ShareholderViewService shareholderViewService;
    private final ShareholderBackfillService backfillService;
    private final com.portal.serasa.infrastructure.integration.cnpj.ReceitaIndexHealth indexHealth;

    /** Sócios da empresa, com a contagem de outras empresas de cada um. */
    @GetMapping("/{cnpj}/socios")
    public ResponseEntity<List<ShareholderResponse>> shareholders(
            @PathVariable @NotBlank @Size(min = 8, max = 18)
            @Pattern(regexp = CNPJ_PATTERN, message = "CNPJ deve conter apenas dígitos ou formatação") String cnpj) {
        if (!shareholderViewService.isAvailable()) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(shareholderViewService.getShareholders(cnpj));
    }

    /** Empresas ligadas à consultada por sócio em comum. */
    @GetMapping("/{cnpj}/grupo-economico")
    public ResponseEntity<List<RelatedCompanyResponse>> economicGroup(
            @PathVariable @NotBlank @Size(min = 8, max = 18)
            @Pattern(regexp = CNPJ_PATTERN, message = "CNPJ deve conter apenas dígitos ou formatação") String cnpj) {
        if (!shareholderViewService.isAvailable()) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(shareholderViewService.getRelatedCompanies(cnpj));
    }

    /**
     * Carga inicial do quadro societário para a carteira inteira. Assíncrona: responde 202 e
     * segue em background. 409 se já houver uma carga em andamento.
     */
    @PostMapping("/socios/carga")
    public ResponseEntity<ShareholderBackfillService.BackfillStatus> backfill() {
        if (!backfillService.start()) {
            return ResponseEntity.status(409).body(backfillService.getLastStatus());
        }
        return ResponseEntity.accepted().body(backfillService.getLastStatus());
    }

    /**
     * Índices exigidos que estão faltando na base da Receita, com o SQL de correção.
     * Vazio = tudo certo. O pipeline pode recriar as tabelas e levar os índices junto, e a
     * degradação é silenciosa (só lentidão), por isso fica consultável.
     */
    @GetMapping("/socios/indices")
    public ResponseEntity<java.util.Map<String, String>> missingIndexes() {
        return ResponseEntity.ok(indexHealth.missingIndexesWithFix());
    }

    /** Andamento da última carga. */
    @GetMapping("/socios/carga")
    public ResponseEntity<ShareholderBackfillService.BackfillStatus> backfillStatus() {
        return ResponseEntity.ok(backfillService.getLastStatus());
    }
}
