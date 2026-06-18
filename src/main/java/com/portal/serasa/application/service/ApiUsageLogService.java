package com.portal.serasa.application.service;

import com.portal.serasa.api.rest.dto.response.ApiUsageLogResponse;
import com.portal.serasa.infrastructure.persistence.entity.ApiUsageLogEntity;
import com.portal.serasa.infrastructure.persistence.repository.ApiUsageLogJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApiUsageLogService {

    private final ApiUsageLogJpaRepository repository;

    @Transactional
    public void save(String userName, String companyName, String cnpj, String queryType, BigDecimal cost) {
        ApiUsageLogEntity log = ApiUsageLogEntity.builder()
                .userName(userName)
                .companyName(companyName)
                .cnpj(cnpj)
                .queryType(queryType)
                .cost(cost != null ? cost : BigDecimal.ZERO)
                .queriedAt(LocalDateTime.now())
                .build();
        repository.save(log);
    }

    public List<ApiUsageLogResponse> findAll() {
        return repository.findAll().stream()
                .sorted((a, b) -> b.getQueriedAt().compareTo(a.getQueriedAt()))
                .map(e -> ApiUsageLogResponse.builder()
                        .id(e.getId().toString())
                        .userName(e.getUserName())
                        .companyName(e.getCompanyName())
                        .documentNumber(e.getCnpj())
                        .entityType(resolveEntityType(e.getCnpj(), e.getQueryType()))
                        .timestamp(e.getQueriedAt().toString())
                        .queryType(e.getQueryType())
                        .cost(e.getCost() != null ? e.getCost().doubleValue() : 0.0)
                        .build())
                .toList();
    }

    private String resolveEntityType(String documentNumber, String queryType) {
        if ("PF".equalsIgnoreCase(queryType)) {
            return "PF";
        }
        if (documentNumber == null) {
            return "PJ";
        }
        String digits = documentNumber.replaceAll("\\D", "");
        return digits.length() == 11 ? "PF" : "PJ";
    }
}
