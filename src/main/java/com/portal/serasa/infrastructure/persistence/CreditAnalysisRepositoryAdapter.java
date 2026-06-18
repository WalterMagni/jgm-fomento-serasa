package com.portal.serasa.infrastructure.persistence;

import com.portal.serasa.application.port.out.CreditAnalysisRepository;
import com.portal.serasa.domain.model.CreditAnalysis;
import com.portal.serasa.infrastructure.persistence.entity.CreditAnalysisEntity;
import com.portal.serasa.infrastructure.persistence.mapper.CreditAnalysisEntityMapper;
import com.portal.serasa.infrastructure.persistence.repository.CreditAnalysisJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
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

    @Override
    public Optional<CreditAnalysis> findLatestByCnpj(String cnpj) {
        return jpaRepository.findFirstByCnpjOrderByConsultaEmDesc(cnpj)
                .map(mapper::toDomain);
    }

    @Override
    public List<CreditAnalysis> findLatestByCnpjIn(Collection<String> cnpjs) {
        if (cnpjs == null || cnpjs.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findLatestByCnpjIn(cnpjs).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteAllByCnpj(String cnpj) {
        jpaRepository.deleteByCnpj(cnpj);
    }

    @Override
    public long countByVisaoCedente(String visaoCedente) {
        return jpaRepository.countByVisaoCedente(visaoCedente);
    }

    @Override
    public List<CreditAnalysis> findLatestByVisaoCedente(String visaoCedente) {
        return jpaRepository.findLatestByVisaoCedente(visaoCedente).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void saveAiAnalysis(Long id, String aiAnalysisJson, LocalDateTime date) {
        jpaRepository.updateAiAnalysis(id, aiAnalysisJson, date);
    }

    @Override
    public PortfolioAnalysisMetrics getPortfolioAnalysisMetrics() {
        CreditAnalysisJpaRepository.PortfolioAnalysisMetricsProjection projection =
                jpaRepository.getPortfolioAnalysisMetrics();
        return new PortfolioAnalysisMetrics(
                toLong(projection.getAnalyzedClients()),
                toLong(projection.getCedenteSimCount()),
                toLong(projection.getHighRiskCount()),
                toLong(projection.getAvgScore())
        );
    }

    private long toLong(Number value) {
        return value != null ? value.longValue() : 0;
    }
}
