package com.portal.serasa.application.port.out;

import com.portal.serasa.domain.model.CreditAnalysis;

import java.util.List;
import java.util.Optional;

public interface CreditAnalysisRepository {

    CreditAnalysis save(CreditAnalysis creditAnalysis);

    Optional<CreditAnalysis> findById(Long id);

    List<CreditAnalysis> findByCnpj(String cnpj);
}
