package com.portal.serasa.api.rest.controller;

import com.portal.serasa.application.port.out.CreditAnalysisRepository;
import com.portal.serasa.application.service.CompanyDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final CompanyDetailService companyDetailService;
    private final CreditAnalysisRepository creditAnalysisRepository;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        long totalClients = companyDetailService.findAll(org.springframework.data.domain.Pageable.unpaged())
                .getTotalElements();

        long cedentesSim = creditAnalysisRepository.countByVisaoCedente("SIM");

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalClients", totalClients);
        summary.put("highRiskClients", 0);
        summary.put("totalDebt", 0.0);
        summary.put("serasaQueriesMonth", 0);
        summary.put("cedentesSim", cedentesSim);

        return ResponseEntity.ok(summary);
    }
}
