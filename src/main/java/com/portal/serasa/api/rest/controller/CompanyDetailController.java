package com.portal.serasa.api.rest.controller;

import com.portal.serasa.api.rest.dto.response.CompanyDetailResponse;
import com.portal.serasa.application.service.CompanyDetailService;
import com.portal.serasa.domain.model.CompanyDetail;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/company")
@RequiredArgsConstructor
@Validated
public class CompanyDetailController {

    private final CompanyDetailService companyDetailService;

    @PostMapping("/enrich/{cnpj}")
    public ResponseEntity<CompanyDetailResponse> enrichByCnpj(
            @PathVariable
            @NotBlank
            @Size(min = 14, max = 18)
            @Pattern(regexp = "^[0-9.\\-/]+$", message = "CNPJ deve conter apenas dígitos ou formatação")
            String cnpj) {
        String normalized = cnpj.replaceAll("\\D", "");
        CompanyDetail detail = companyDetailService.enrichByCnpj(normalized);
        return ResponseEntity.ok(toResponse(detail));
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

    private CompanyDetailResponse toResponse(CompanyDetail d) {
        return CompanyDetailResponse.builder()
                .id(d.getId())
                .documentNumber(d.getDocumentNumber())
                .updatedAt(d.getUpdatedAt())
                .alias(d.getAlias())
                .founded(d.getFounded())
                .companyName(d.getCompanyName())
                .companyEquity(d.getCompanyEquity())
                .natureId(d.getNatureId())
                .natureText(d.getNatureText())
                .sizeText(d.getSizeText())
                .street(d.getStreet())
                .number(d.getNumber())
                .details(d.getDetails())
                .district(d.getDistrict())
                .city(d.getCity())
                .state(d.getState())
                .zip(d.getZip())
                .latitude(d.getLatitude())
                .longitude(d.getLongitude())
                .members(d.getMembers())
                .phones(d.getPhones())
                .emails(d.getEmails())
                .mainActivity(d.getMainActivity())
                .sideActivities(d.getSideActivities())
                .build();
    }
}
