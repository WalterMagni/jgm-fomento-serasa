package com.portal.serasa.api.rest.controller;

import com.portal.serasa.api.rest.dto.request.PaymentPlaceBulkDecisionRequest;
import com.portal.serasa.api.rest.dto.request.PaymentPlaceEntryDecisionRequest;
import com.portal.serasa.api.rest.dto.response.PaymentPlaceBatchDetailResponse;
import com.portal.serasa.api.rest.dto.response.PaymentPlaceBatchIndicatorsResponse;
import com.portal.serasa.api.rest.dto.response.PaymentPlaceBatchResponse;
import com.portal.serasa.api.rest.dto.response.PaymentPlaceCompanySummaryResponse;
import com.portal.serasa.api.rest.dto.response.PaymentPlaceEntryResponse;
import com.portal.serasa.application.service.PaymentPlaceAnalysisService;
import com.portal.serasa.infrastructure.persistence.entity.PaymentPlaceBatchEntity;
import com.portal.serasa.infrastructure.persistence.entity.PaymentPlaceEntryEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/praca-pagamento")
@RequiredArgsConstructor
public class PaymentPlaceAnalysisController {

    private final PaymentPlaceAnalysisService paymentPlaceAnalysisService;
    private final com.portal.serasa.application.service.CompanyBranchService companyBranchService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @GetMapping("/filiais/{cnpj}")
    public ResponseEntity<List<com.portal.serasa.api.rest.dto.response.CompanyBranchResponse>> branches(
            @PathVariable String cnpj) {
        if (!companyBranchService.isAvailable()) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(companyBranchService.getBranches(cnpj));
    }

    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PaymentPlaceBatchDetailResponse> importPdf(@RequestParam("file") MultipartFile file)
            throws IOException {
        try (var inputStream = file.getInputStream()) {
            String fileName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                    ? file.getName()
                    : file.getOriginalFilename();
            var result = paymentPlaceAnalysisService.importPdf(fileName, inputStream, getAuthenticatedUser());
            return ResponseEntity.ok(PaymentPlaceBatchDetailResponse.builder()
                    .batch(toBatchResponse(result.getBatch()))
                    .entries(result.getEntries().stream().map(this::toEntryResponse).toList())
                    .build());
        }
    }

    @GetMapping("/empresa/{cnpj}")
    public ResponseEntity<PaymentPlaceCompanySummaryResponse> companySummary(
            @PathVariable String cnpj,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
            @RequestParam(required = false) String decisao,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        var s = paymentPlaceAnalysisService.getCompanySummary(cnpj, from, to, decisao, page, size);
        return ResponseEntity.ok(PaymentPlaceCompanySummaryResponse.builder()
                .documentNumber(s.documentNumber())
                .sacadoCount(s.sacadoCount())
                .sacadoValue(s.sacadoValue())
                .cedenteCount(s.cedenteCount())
                .cedenteValue(s.cedenteValue())
                .totalCount(s.sacadoCount() + s.cedenteCount())
                .totalValue(s.sacadoValue().add(s.cedenteValue()))
                .entries(s.entries().stream().map(this::toEntryResponse).toList())
                .page(s.page())
                .size(s.size())
                .totalPages(s.totalPages())
                .totalFilteredElements(s.totalFilteredElements())
                .build());
    }

    @GetMapping("/lotes")
    public ResponseEntity<List<PaymentPlaceBatchResponse>> listBatches() {
        return ResponseEntity.ok(paymentPlaceAnalysisService.listBatches().stream()
                .map(this::toBatchResponse)
                .toList());
    }

    @GetMapping("/lotes/{batchId}")
    public ResponseEntity<PaymentPlaceBatchDetailResponse> getBatch(@PathVariable UUID batchId) {
        var result = paymentPlaceAnalysisService.getBatch(batchId);
        return ResponseEntity.ok(PaymentPlaceBatchDetailResponse.builder()
                .batch(toBatchResponse(result.getBatch()))
                .entries(result.getEntries().stream().map(this::toEntryResponse).toList())
                .build());
    }

    @GetMapping("/lotes/{batchId}/indicadores")
    public ResponseEntity<PaymentPlaceBatchIndicatorsResponse> getBatchIndicators(@PathVariable UUID batchId) {
        var result = paymentPlaceAnalysisService.getBatchIndicators(batchId);
        return ResponseEntity.ok(PaymentPlaceBatchIndicatorsResponse.builder()
                .batchId(result.batch().getId())
                .fileName(result.batch().getFileName())
                .totalEntries(result.totalEntries())
                .locatedAgencyCount(result.locatedAgencyCount())
                .locatedAgencyPct(result.locatedAgencyPct())
                .lowReliabilityCount(result.lowReliabilityCount())
                .lowReliabilityPct(result.lowReliabilityPct())
                .comparableDecisionCount(result.comparableDecisionCount())
                .agreementCount(result.agreementCount())
                .agreementPct(result.agreementPct())
                .disagreementCount(result.disagreementCount())
                .disagreementPct(result.disagreementPct())
                .topRecurringBankAgencies(result.topRecurringBankAgencies().stream()
                        .map(item -> PaymentPlaceBatchIndicatorsResponse.BankAgencyIndicatorResponse.builder()
                                .bankAgency(item.bankAgency())
                                .bankName(item.bankName())
                                .bankCode(item.bankCode())
                                .agencyCode(item.agencyCode())
                                .totalEntries(item.totalEntries())
                                .decidedEntries(item.decidedEntries())
                                .agreementCount(item.agreementCount())
                                .disagreementCount(item.disagreementCount())
                                .disagreementPct(item.disagreementPct())
                                .build())
                        .toList())
                .topDivergentBankAgencies(result.topDivergentBankAgencies().stream()
                        .map(item -> PaymentPlaceBatchIndicatorsResponse.BankAgencyIndicatorResponse.builder()
                                .bankAgency(item.bankAgency())
                                .bankName(item.bankName())
                                .bankCode(item.bankCode())
                                .agencyCode(item.agencyCode())
                                .totalEntries(item.totalEntries())
                                .decidedEntries(item.decidedEntries())
                                .agreementCount(item.agreementCount())
                                .disagreementCount(item.disagreementCount())
                                .disagreementPct(item.disagreementPct())
                                .build())
                        .toList())
                .build());
    }

    @PatchMapping("/lotes/{batchId}/arquivar")
    public ResponseEntity<PaymentPlaceBatchResponse> archiveBatch(@PathVariable UUID batchId) {
        return ResponseEntity.ok(toBatchResponse(paymentPlaceAnalysisService.setBatchArchived(batchId, true)));
    }

    @PatchMapping("/lotes/{batchId}/desarquivar")
    public ResponseEntity<PaymentPlaceBatchResponse> unarchiveBatch(@PathVariable UUID batchId) {
        return ResponseEntity.ok(toBatchResponse(paymentPlaceAnalysisService.setBatchArchived(batchId, false)));
    }

    @DeleteMapping("/lotes/{batchId}")
    public ResponseEntity<Void> deleteBatch(@PathVariable UUID batchId) {
        paymentPlaceAnalysisService.deleteBatch(batchId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/lancamentos/{entryId}/decisao")
    public ResponseEntity<PaymentPlaceEntryResponse> decideEntry(
            @PathVariable UUID entryId,
            @Valid @RequestBody PaymentPlaceEntryDecisionRequest request) {
        return ResponseEntity.ok(toEntryResponse(
                paymentPlaceAnalysisService.decideEntry(entryId, request.getDecision(), request.getNotes(), getAuthenticatedUser())));
    }

    @PostMapping("/lancamentos/{entryId}/agencia-bacen")
    public ResponseEntity<PaymentPlaceEntryResponse> enrichAgency(@PathVariable UUID entryId) {
        return ResponseEntity.ok(toEntryResponse(paymentPlaceAnalysisService.enrichAgencyFromBacen(entryId)));
    }

    @PostMapping("/lancamentos/{entryId}/cnpj-sacado")
    public ResponseEntity<PaymentPlaceEntryResponse> enrichPayerCnpj(@PathVariable UUID entryId) {
        return ResponseEntity.ok(toEntryResponse(paymentPlaceAnalysisService.enrichPayerCnpj(entryId)));
    }

    @PostMapping("/lancamentos/{entryId}/cnpj-cedente")
    public ResponseEntity<PaymentPlaceEntryResponse> linkCedenteCnpj(
            @PathVariable UUID entryId,
            @RequestBody java.util.Map<String, Object> body) {
        String cnpj = body.get("cnpj") == null ? null : body.get("cnpj").toString();
        boolean create = Boolean.parseBoolean(String.valueOf(body.getOrDefault("create", false)));
        return ResponseEntity.ok(toEntryResponse(paymentPlaceAnalysisService.linkCedenteCnpj(entryId, cnpj, create)));
    }

    @DeleteMapping("/lancamentos/{entryId}/decisao")
    public ResponseEntity<PaymentPlaceEntryResponse> reopenEntry(@PathVariable UUID entryId) {
        return ResponseEntity.ok(toEntryResponse(paymentPlaceAnalysisService.reopenEntry(entryId)));
    }

    @PostMapping("/lancamentos/{entryId}/analise-ia")
    public ResponseEntity<?> analyzeWithAi(@PathVariable UUID entryId) {
        try {
            return ResponseEntity.ok(toEntryResponse(paymentPlaceAnalysisService.analyzeWithAi(entryId)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/lancamentos/decisoes")
    public ResponseEntity<List<PaymentPlaceEntryResponse>> bulkDecide(
            @Valid @RequestBody PaymentPlaceBulkDecisionRequest request) {
        java.util.Map<UUID, String> map = new java.util.LinkedHashMap<>();
        for (PaymentPlaceBulkDecisionRequest.Item item : request.getDecisions()) {
            if (item.getEntryId() != null) {
                map.put(item.getEntryId(), item.getDecision());
            }
        }
        return ResponseEntity.ok(paymentPlaceAnalysisService.bulkDecide(map, getAuthenticatedUser()).stream()
                .map(this::toEntryResponse)
                .toList());
    }

    private UserEntity getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserEntity user)) {
            return null;
        }
        return user;
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private PaymentPlaceBatchResponse toBatchResponse(PaymentPlaceBatchEntity entity) {
        return PaymentPlaceBatchResponse.builder()
                .id(entity.getId())
                .fileName(entity.getFileName())
                .importedByName(entity.getImportedByName())
                .importedByEmail(entity.getImportedByEmail())
                .importedAt(entity.getImportedAt())
                .status(entity.getStatus())
                .totalEntries(entity.getTotalEntries())
                .auditEntries(entity.getAuditEntries())
                .unlocatedAgencyEntries(entity.getUnlocatedAgencyEntries())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private PaymentPlaceEntryResponse toEntryResponse(PaymentPlaceEntryEntity entity) {
        return PaymentPlaceEntryResponse.builder()
                .id(entity.getId())
                .batchId(entity.getBatchId())
                .section(entity.getSection())
                .externalId(entity.getExternalId())
                .clientCode(entity.getClientCode())
                .titleNumber(entity.getTitleNumber())
                .dueDate(entity.getDueDate())
                .titleValue(entity.getTitleValue())
                .paidValue(entity.getPaidValue())
                .occurrence(entity.getOccurrence())
                .payerDocument(entity.getPayerDocument())
                .payerName(entity.getPayerName())
                .clientCity(entity.getClientCity())
                .agencyCityPdf(entity.getAgencyCityPdf())
                .payerCity(entity.getPayerCity())
                .bankAgency(entity.getBankAgency())
                .bankCode(entity.getBankCode())
                .agencyCode(entity.getAgencyCode())
                .occurrenceComplement(entity.getOccurrenceComplement())
                .analysisStatus(entity.getAnalysisStatus())
                .analystDecision(entity.getAnalystDecision())
                .analystNotes(entity.getAnalystNotes())
                .decidedByName(entity.getDecidedByName())
                .decidedAt(entity.getDecidedAt())
                .bankName(entity.getBankName())
                .bacenAgencyName(entity.getBacenAgencyName())
                .bacenInstitutionName(entity.getBacenInstitutionName())
                .bacenInstitutionType(entity.getBacenInstitutionType())
                .institutionCategory(entity.getInstitutionCategory())
                .geographicReliability(entity.getGeographicReliability())
                .geographicReliabilityReason(entity.getGeographicReliabilityReason())
                .automaticSuggestion(entity.getAutomaticSuggestion())
                .automaticConfidence(entity.getAutomaticConfidence())
                .automaticEvidence(entity.getAutomaticEvidence())
                .scoreSacado(entity.getScoreSacado())
                .scoreCedente(entity.getScoreCedente())
                .aiAnalysis(parseJson(entity.getAiAnalysis()))
                .aiAnalyzedAt(entity.getAiAnalyzedAt())
                .bacenAgencyCity(entity.getBacenAgencyCity())
                .bacenAgencyAddress(entity.getBacenAgencyAddress())
                .bacenAgencyZipCode(entity.getBacenAgencyZipCode())
                .distanceClientAgencyKm(entity.getDistanceClientAgencyKm())
                .distanceAgencyPayerKm(entity.getDistanceAgencyPayerKm())
                .distanceClientPayerKm(entity.getDistanceClientPayerKm())
                .clientLatitude(entity.getClientLatitude())
                .clientLongitude(entity.getClientLongitude())
                .agencyLatitude(entity.getAgencyLatitude())
                .agencyLongitude(entity.getAgencyLongitude())
                .payerLatitude(entity.getPayerLatitude())
                .payerLongitude(entity.getPayerLongitude())
                .clientAddress(entity.getClientAddress())
                .clientName(entity.getClientName())
                .clientDocument(entity.getClientDocument())
                .payerAddress(entity.getPayerAddress())
                .agencyAddressResolved(entity.getAgencyAddressResolved())
                .agencyEnrichedAt(entity.getAgencyEnrichedAt())
                .reopenedAt(entity.getReopenedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
