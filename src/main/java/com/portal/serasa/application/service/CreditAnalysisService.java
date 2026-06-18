package com.portal.serasa.application.service;

import com.portal.serasa.application.port.in.CreditAnalysisUseCase;
import com.portal.serasa.application.port.out.CreditAnalysisRepository;
import com.portal.serasa.domain.model.CreditAnalysis;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CreditAnalysisService implements CreditAnalysisUseCase {

    private final CreditAnalysisRepository creditAnalysisRepository;

    @Override
    public Optional<CreditAnalysis> buscarPorId(Long id) {
        return creditAnalysisRepository.findById(id);
    }

    @Override
    public List<CreditAnalysis> buscarPorCnpj(String cnpj) {
        return creditAnalysisRepository.findByCnpj(cnpj);
    }
}
