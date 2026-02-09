package com.portal.serasa.api.rest.controller;

import com.portal.serasa.application.service.ApiRequestCounterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/api-usage")
@RequiredArgsConstructor
public class ApiUsageController {

    private final ApiRequestCounterService apiRequestCounterService;

    @GetMapping
    public ResponseEntity<Map<String, Long>> getApiUsage() {
        return ResponseEntity.ok(apiRequestCounterService.getCounts());
    }
}
