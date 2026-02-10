package com.portal.serasa.api.rest.controller;

import com.portal.serasa.api.rest.dto.request.CompanyDetailCreateRequest;
import com.portal.serasa.api.rest.dto.request.CompanyDetailUpdateRequest;
import com.portal.serasa.api.rest.dto.response.ClientProfileResponse;
import com.portal.serasa.api.rest.dto.response.ClientResponse;
import com.portal.serasa.api.rest.dto.response.CompanyDetailResponse;
import com.portal.serasa.api.rest.dto.response.CreditAnalysisResponse;
import com.portal.serasa.api.rest.mapper.CreditAnalysisDtoMapper;
import com.portal.serasa.application.service.ClientProfileService;
import com.portal.serasa.application.service.ClientProfileService.ResyncTarget;
import com.portal.serasa.application.service.CompanyDetailService;
import com.portal.serasa.domain.model.Client;
import com.portal.serasa.domain.model.CompanyDetail;
import com.portal.serasa.domain.model.CreditAnalysis;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/company")
@RequiredArgsConstructor
@Validated
public class CompanyDetailController {

    private final CompanyDetailService companyDetailService;
    private final ClientProfileService clientProfileService;
    private final CreditAnalysisDtoMapper creditAnalysisDtoMapper;

    /** Enriquece dados da empresa consultando a API CNPJ Já. */
    @PostMapping("/enrich/cnpja/{cnpj}")
    public ResponseEntity<CompanyDetailResponse> enrichByCnpja(
            @PathVariable
            @NotBlank
            @Size(min = 14, max = 18)
            @Pattern(regexp = "^[0-9.\\-/]+$", message = "CNPJ deve conter apenas dígitos ou formatação")
            String cnpj) {
        String normalized = cnpj.replaceAll("\\D", "");
        CompanyDetail detail = companyDetailService.enrichByCnpj(normalized);
        return ResponseEntity.ok(toResponse(detail));
    }

    /** Enriquece dados da empresa consultando a API Serasa (Credit Rating). Cliente deve existir. */
    @PostMapping("/enrich/serasa/{cnpj}")
    public ResponseEntity<CreditAnalysisResponse> enrichBySerasa(
            @PathVariable
            @NotBlank
            @Size(min = 14, max = 18)
            @Pattern(regexp = "^[0-9.\\-/]+$", message = "CNPJ deve conter apenas dígitos ou formatação")
            String cnpj) {
        String normalized = cnpj.replaceAll("\\D", "");
        CreditAnalysis analysis = clientProfileService.enrichBySerasa(normalized);
        return ResponseEntity.ok(creditAnalysisDtoMapper.toResponse(analysis));
    }

    /** Perfil completo: dados CNPJ Já + Serasa. */
    @GetMapping("/{cnpj}/profile")
    public ResponseEntity<ClientProfileResponse> getProfile(
            @PathVariable
            @NotBlank
            @Size(min = 14, max = 18)
            String cnpj) {
        String normalized = cnpj.replaceAll("\\D", "");
        ClientProfileService.ClientProfile profile = clientProfileService.getProfileByCnpj(normalized);
        return ResponseEntity.ok(toProfileResponse(profile));
    }

    @GetMapping
    public ResponseEntity<Page<CompanyDetailResponse>> findAll(Pageable pageable) {
        return ResponseEntity.ok(companyDetailService.findAll(pageable).map(this::toResponse));
    }

    @GetMapping("/{cnpj}")
    public ResponseEntity<CompanyDetailResponse> findByCnpj(
            @PathVariable
            @NotBlank
            @Size(min = 14, max = 18)
            String cnpj) {
        String normalized = cnpj.replaceAll("\\D", "");
        CompanyDetail detail = companyDetailService.findByDocumentNumber(normalized)
                .orElseThrow(() -> new EntityNotFoundException("Empresa não encontrada para CNPJ: " + normalized));
        return ResponseEntity.ok(toResponse(detail));
    }

    @PostMapping
    public ResponseEntity<CompanyDetailResponse> create(@Valid @RequestBody CompanyDetailCreateRequest request) {
        CompanyDetail detail = CompanyDetail.builder()
                .documentNumber(request.getDocumentNumber().replaceAll("\\D", ""))
                .companyName(request.getCompanyName())
                .build();
        CompanyDetail saved = companyDetailService.create(detail);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    /** Correção manual parcial dos dados cadastrais (apenas campos enviados). */
    @PatchMapping("/{cnpj}")
    public ResponseEntity<CompanyDetailResponse> patch(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @Valid @RequestBody CompanyDetailUpdateRequest request) {
        CompanyDetail updates = mapUpdateRequestToDomain(request);
        CompanyDetail saved = companyDetailService.update(cnpj.replaceAll("\\D", ""), updates);
        return ResponseEntity.ok(toResponse(saved));
    }

    @PutMapping("/{cnpj}")
    public ResponseEntity<CompanyDetailResponse> update(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @Valid @RequestBody CompanyDetailUpdateRequest request) {
        CompanyDetail updates = mapUpdateRequestToDomain(request);
        CompanyDetail saved = companyDetailService.update(cnpj.replaceAll("\\D", ""), updates);
        return ResponseEntity.ok(toResponse(saved));
    }

    /** Re-sincronização: consulta APIs externas e atualiza dados. target: CNPJA, SERASA ou ALL. */
    @PostMapping("/{cnpj}/resync")
    public ResponseEntity<ClientProfileResponse> resync(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @RequestParam(defaultValue = "ALL") String target) {
        String normalized = cnpj.replaceAll("\\D", "");
        ResyncTarget resyncTarget = parseResyncTarget(target);
        ClientProfileService.ClientProfile profile = clientProfileService.resync(normalized, resyncTarget);
        return ResponseEntity.ok(toProfileResponse(profile));
    }

    private ResyncTarget parseResyncTarget(String target) {
        return switch (target.toUpperCase()) {
            case "CNPJA" -> ResyncTarget.CNPJA;
            case "SERASA" -> ResyncTarget.SERASA;
            case "ALL" -> ResyncTarget.ALL;
            default -> throw new IllegalArgumentException("target inválido. Use: CNPJA, SERASA ou ALL");
        };
    }

    @DeleteMapping("/{cnpj}")
    public ResponseEntity<Void> delete(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj) {
        companyDetailService.deleteByDocumentNumber(cnpj.replaceAll("\\D", ""));
        return ResponseEntity.noContent().build();
    }

    private CompanyDetail mapUpdateRequestToDomain(CompanyDetailUpdateRequest r) {
        return CompanyDetail.builder()
                .companyName(r.getCompanyName())
                .alias(r.getAlias())
                .founded(r.getFounded())
                .head(r.getHead())
                .statusDate(r.getStatusDate())
                .statusId(r.getStatusId())
                .statusText(r.getStatusText())
                .companyEquity(r.getCompanyEquity())
                .natureId(r.getNatureId())
                .natureText(r.getNatureText())
                .sizeAcronym(r.getSizeAcronym())
                .sizeText(r.getSizeText())
                .street(r.getStreet())
                .number(r.getNumber())
                .details(r.getDetails())
                .district(r.getDistrict())
                .city(r.getCity())
                .state(r.getState())
                .zip(r.getZip())
                .countryId(r.getCountryId())
                .countryName(r.getCountryName())
                .latitude(r.getLatitude())
                .longitude(r.getLongitude())
                .members(r.getMembers())
                .phones(r.getPhones())
                .emails(r.getEmails())
                .mainActivity(r.getMainActivity())
                .sideActivities(r.getSideActivities())
                .build();
    }

    private ClientProfileResponse toProfileResponse(ClientProfileService.ClientProfile profile) {
        return ClientProfileResponse.builder()
                .client(profile.getClient() != null ? toClientResponse(profile.getClient()) : null)
                .companyDetail(profile.getCompanyDetail() != null ? toResponse(profile.getCompanyDetail()) : null)
                .creditAnalysis(profile.getCreditAnalysis() != null ? creditAnalysisDtoMapper.toResponse(profile.getCreditAnalysis()) : null)
                .build();
    }

    private ClientResponse toClientResponse(Client c) {
        return ClientResponse.builder()
                .id(c.getId())
                .documentNumber(c.getDocumentNumber())
                .name(c.getName())
                .email(c.getEmail())
                .phones(c.getPhones())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private CompanyDetailResponse toResponse(CompanyDetail d) {
        return CompanyDetailResponse.builder()
                .id(d.getId())
                .documentNumber(d.getDocumentNumber())
                .updatedAt(d.getUpdatedAt())
                .alias(d.getAlias())
                .founded(d.getFounded())
                .head(d.getHead())
                .statusDate(d.getStatusDate())
                .statusId(d.getStatusId())
                .statusText(d.getStatusText())
                .companyId(d.getCompanyId())
                .companyName(d.getCompanyName())
                .companyEquity(d.getCompanyEquity())
                .natureId(d.getNatureId())
                .natureText(d.getNatureText())
                .sizeAcronym(d.getSizeAcronym())
                .sizeText(d.getSizeText())
                .street(d.getStreet())
                .number(d.getNumber())
                .details(d.getDetails())
                .district(d.getDistrict())
                .city(d.getCity())
                .state(d.getState())
                .zip(d.getZip())
                .countryId(d.getCountryId())
                .countryName(d.getCountryName())
                .latitude(d.getLatitude())
                .longitude(d.getLongitude())
                .members(d.getMembers())
                .phones(d.getPhones())
                .emails(d.getEmails())
                .mainActivity(d.getMainActivity())
                .sideActivities(d.getSideActivities())
                .createdAt(d.getCreatedAt())
                .modifiedAt(d.getModifiedAt())
                .build();
    }
}
