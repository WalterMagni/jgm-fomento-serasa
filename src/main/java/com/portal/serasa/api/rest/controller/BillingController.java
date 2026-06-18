package com.portal.serasa.api.rest.controller;

import com.portal.serasa.api.rest.dto.response.ApiUsageLogResponse;
import com.portal.serasa.api.rest.dto.response.BillingSettingsResponse;
import com.portal.serasa.application.service.ApiUsageLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {

    private final ApiUsageLogService apiUsageLogService;

    @Value("${billing.serasa.cost-per-query:0.00}")
    private BigDecimal serasaCostPerQuery;

    @Value("${billing.serasa.pf-cost-per-query:15.52}")
    private BigDecimal serasaPfCostPerQuery;

    @GetMapping("/usage")
    public ResponseEntity<List<ApiUsageLogResponse>> getUsage() {
        return ResponseEntity.ok(apiUsageLogService.findAll());
    }

    @GetMapping("/settings")
    public ResponseEntity<BillingSettingsResponse> getBillingSettings() {
        return ResponseEntity.ok(BillingSettingsResponse.builder()
                .serasaCostPerQuery(serasaCostPerQuery.doubleValue())
                .serasaPfCostPerQuery(serasaPfCostPerQuery.doubleValue())
                .build());
    }
}
