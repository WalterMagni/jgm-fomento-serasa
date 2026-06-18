package com.portal.serasa.api.rest.controller;

import com.portal.serasa.api.rest.dto.request.StandardTermRequest;
import com.portal.serasa.api.rest.dto.response.StandardTermResponse;
import com.portal.serasa.application.service.StandardTermService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/standard-terms")
@RequiredArgsConstructor
public class StandardTermController {

    private final StandardTermService standardTermService;

    @GetMapping
    public ResponseEntity<List<StandardTermResponse>> getAllTerms() {
        return ResponseEntity.ok(standardTermService.getAllTerms());
    }

    @PutMapping
    public ResponseEntity<StandardTermResponse> updateTerm(@Valid @RequestBody StandardTermRequest request) {
        return ResponseEntity.ok(standardTermService.updateTerm(request));
    }
}
