package com.portal.serasa.application.service;

import com.portal.serasa.api.rest.dto.request.StandardTermRequest;
import com.portal.serasa.api.rest.dto.response.StandardTermResponse;
import com.portal.serasa.infrastructure.persistence.entity.StandardTermEntity;
import com.portal.serasa.infrastructure.persistence.repository.StandardTermRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StandardTermService {

    private final StandardTermRepository standardTermRepository;

    @Transactional(readOnly = true)
    public List<StandardTermResponse> getAllTerms() {
        return standardTermRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public StandardTermResponse updateTerm(StandardTermRequest request) {
        log.info("Updating standard term for CNPJ: {}", request.getCnpj());
        
        StandardTermEntity entity = standardTermRepository.findByCnpj(request.getCnpj())
                .orElseGet(() -> {
                    StandardTermEntity newEntity = new StandardTermEntity();
                    newEntity.setCnpj(request.getCnpj());
                    return newEntity;
                });
        
        entity.setTermText(request.getTermText());
        entity = standardTermRepository.save(entity);
        
        return mapToResponse(entity);
    }

    private StandardTermResponse mapToResponse(StandardTermEntity entity) {
        return StandardTermResponse.builder()
                .cnpj(entity.getCnpj())
                .termText(entity.getTermText())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
