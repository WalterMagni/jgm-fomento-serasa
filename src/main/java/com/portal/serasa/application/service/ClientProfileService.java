package com.portal.serasa.application.service;

import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.domain.model.Client;
import com.portal.serasa.domain.model.CompanyDetail;
import com.portal.serasa.domain.model.CreditAnalysis;
import com.portal.serasa.application.port.out.ClientRepository;
import com.portal.serasa.application.port.out.CreditAnalysisRepository;
import com.portal.serasa.infrastructure.integration.serasa.SerasaCreditRatingClient;
import com.portal.serasa.infrastructure.integration.serasa.SerasaCreditRatingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Serviço que orquestra dados do cliente vindos de CNPJ Já e Serasa.
 * Mapa de informações: quando buscamos por CNPJ, trazemos dados de ambas as APIs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClientProfileService {

    private final ClientRepository clientRepository;
    private final CompanyDetailService companyDetailService;
    private final CreditAnalysisRepository creditAnalysisRepository;
    private final SerasaCreditRatingClient serasaCreditRatingClient;
    private final SerasaCreditRatingMapper serasaCreditRatingMapper;

    /**
     * Enriquece dados da empresa consultando a API Serasa (Credit Rating).
     * Requer que o cliente já esteja cadastrado (CNPJ deve existir em clients).
     */
    public CreditAnalysis enrichBySerasa(String cnpj) {
        String documentNumber = normalizeCnpj(cnpj);
        if (documentNumber == null || documentNumber.length() != 14) {
            throw new IllegalArgumentException("CNPJ deve conter 14 dígitos numéricos");
        }

        Client client = clientRepository.findByDocumentNumber(documentNumber)
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado para CNPJ: " + documentNumber + ". Cadastre o cliente antes de consultar a Serasa."));

        log.info("Enriquecendo dados Serasa para cliente CNPJ: {}", documentNumber);

        String rawJson = serasaCreditRatingClient.consultarCreditRating(documentNumber);
        CreditAnalysis analysis = serasaCreditRatingMapper.toDomain(client.getId(), documentNumber, rawJson);
        return creditAnalysisRepository.save(analysis);
    }

    /**
     * Re-sincroniza dados da empresa conforme target: CNPJA (dados cadastrais), SERASA (nova análise de crédito) ou ALL (ambos).
     * Retorna o perfil atualizado.
     */
    public ClientProfile resync(String cnpj, ResyncTarget target) {
        String documentNumber = normalizeCnpj(cnpj);
        if (documentNumber == null || documentNumber.length() != 14) {
            throw new IllegalArgumentException("CNPJ deve conter 14 dígitos numéricos");
        }

        if (target == ResyncTarget.CNPJA || target == ResyncTarget.ALL) {
            companyDetailService.enrichByCnpj(documentNumber);
        }
        if (target == ResyncTarget.SERASA || target == ResyncTarget.ALL) {
            enrichBySerasa(documentNumber);
        }

        return getProfileByCnpj(documentNumber);
    }

    /**
     * Retorna o perfil completo do cliente por CNPJ: dados CNPJ Já + Serasa.
     */
    public ClientProfile getProfileByCnpj(String cnpj) {
        String documentNumber = normalizeCnpj(cnpj);

        Optional<CompanyDetail> companyDetail = companyDetailService.findByDocumentNumber(documentNumber);
        Optional<CreditAnalysis> creditAnalysis = creditAnalysisRepository.findLatestByCnpj(documentNumber);
        Optional<Client> client = clientRepository.findByDocumentNumber(documentNumber);

        return ClientProfile.builder()
                .client(client.orElse(null))
                .companyDetail(companyDetail.orElse(null))
                .creditAnalysis(creditAnalysis.orElse(null))
                .build();
    }

    private String normalizeCnpj(String cnpj) {
        if (cnpj == null || cnpj.isBlank()) return null;
        String digits = cnpj.replaceAll("\\D", "");
        return digits.length() >= 14 ? digits.substring(0, 14) : digits;
    }

    @lombok.Data
    @lombok.Builder
    public static class ClientProfile {
        private Client client;
        private CompanyDetail companyDetail;
        private CreditAnalysis creditAnalysis;
    }

    public enum ResyncTarget {
        CNPJA, SERASA, ALL
    }
}
