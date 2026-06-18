package com.portal.serasa.application.port.out;

import com.portal.serasa.domain.model.CreditAnalysis;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CreditAnalysisRepository {

    CreditAnalysis save(CreditAnalysis creditAnalysis);

    Optional<CreditAnalysis> findById(Long id);

    List<CreditAnalysis> findByCnpj(String cnpj);

    Optional<CreditAnalysis> findLatestByCnpj(String cnpj);

    List<CreditAnalysis> findLatestByCnpjIn(Collection<String> cnpjs);

    void deleteAllByCnpj(String cnpj);

    long countByVisaoCedente(String visaoCedente);

    List<CreditAnalysis> findLatestByVisaoCedente(String visaoCedente);

    void saveAiAnalysis(Long id, String aiAnalysisJson, LocalDateTime date);

    PortfolioAnalysisMetrics getPortfolioAnalysisMetrics();

    record PortfolioAnalysisMetrics(
            long analyzedClients,
            long cedenteSimCount,
            long highRiskCount,
            long avgScore
    ) {}
}
