package com.portal.serasa.application.service;

import com.portal.serasa.infrastructure.persistence.entity.ApiRequestCounterEntity;
import com.portal.serasa.infrastructure.persistence.repository.ApiRequestCounterJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiRequestCounterService {

    public static final String PROVIDER_CNPJA = "CNPJA";
    public static final String PROVIDER_SERASA = "SERASA";

    private final ApiRequestCounterJpaRepository repository;

    @Transactional
    public void increment(String provider) {
        int updated = repository.incrementCount(provider);
        if (updated == 0) {
            log.warn("Provider {} não encontrado na tabela api_request_counters", provider);
        }
    }

    public Map<String, Long> getCounts() {
        return repository.findAll().stream()
                .collect(Collectors.toMap(ApiRequestCounterEntity::getProvider, ApiRequestCounterEntity::getRequestCount));
    }
}
