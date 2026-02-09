package com.portal.serasa.api.rest.controller;

import com.portal.serasa.api.rest.dto.request.CompanyDetailCreateRequest;
import com.portal.serasa.api.rest.dto.request.CompanyDetailUpdateRequest;
import com.portal.serasa.api.rest.dto.response.CompanyDetailResponse;
import com.portal.serasa.application.service.CompanyDetailService;
import com.portal.serasa.domain.model.CompanyDetail;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/company")
@RequiredArgsConstructor
@Validated
public class CompanyDetailController {

    private final CompanyDetailService companyDetailService;

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

    @PutMapping("/{cnpj}")
    public ResponseEntity<CompanyDetailResponse> update(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @Valid @RequestBody CompanyDetailUpdateRequest request) {
        CompanyDetail updates = mapUpdateRequestToDomain(request);
        CompanyDetail saved = companyDetailService.update(cnpj.replaceAll("\\D", ""), updates);
        return ResponseEntity.ok(toResponse(saved));
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
