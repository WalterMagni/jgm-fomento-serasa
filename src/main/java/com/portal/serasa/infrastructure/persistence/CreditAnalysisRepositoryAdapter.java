package com.portal.serasa.infrastructure.persistence;

import com.portal.serasa.application.port.out.CreditAnalysisRepository;
import com.portal.serasa.domain.model.CreditAnalysis;
import com.portal.serasa.infrastructure.persistence.entity.CreditAnalysisEntity;
import com.portal.serasa.infrastructure.persistence.mapper.CreditAnalysisEntityMapper;
import com.portal.serasa.infrastructure.persistence.repository.CreditAnalysisJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CreditAnalysisRepositoryAdapter implements CreditAnalysisRepository {

    private final CreditAnalysisJpaRepository jpaRepository;
    private final CreditAnalysisEntityMapper mapper;

    @Override
    public CreditAnalysis save(CreditAnalysis creditAnalysis) {
        CreditAnalysisEntity entity = mapper.toEntity(creditAnalysis);
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(java.time.LocalDateTime.now());
        }
        CreditAnalysisEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<CreditAnalysis> findById(Long id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<CreditAnalysis> findByCnpj(String cnpj) {
        return jpaRepository.findByCnpj(cnpj).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }
}
