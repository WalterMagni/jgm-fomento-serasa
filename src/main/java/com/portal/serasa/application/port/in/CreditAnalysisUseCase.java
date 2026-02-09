package com.portal.serasa.application.port.in;

import com.portal.serasa.domain.model.CreditAnalysis;

import java.util.List;
import java.util.Optional;

public interface CreditAnalysisUseCase {

    CreditAnalysis consultarESalvar(String cnpj);

    Optional<CreditAnalysis> buscarPorId(Long id);

    List<CreditAnalysis> buscarPorCnpj(String cnpj);
}
