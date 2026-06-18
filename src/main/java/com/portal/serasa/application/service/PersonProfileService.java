package com.portal.serasa.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.portal.serasa.application.port.out.ClientRepository;
import com.portal.serasa.application.port.out.PersonAnalysisRepository;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.domain.model.PersonAnalysis;
import com.portal.serasa.infrastructure.integration.serasa.SerasaPersonCreditRatingClient;
import com.portal.serasa.infrastructure.integration.serasa.SerasaPersonCreditRatingMapper;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonProfileService {

    private final PersonAnalysisRepository personAnalysisRepository;
    private final ClientRepository clientRepository;
    private final SerasaPersonCreditRatingClient serasaPersonCreditRatingClient;
    private final SerasaPersonCreditRatingMapper serasaPersonCreditRatingMapper;
    private final ApiUsageLogService apiUsageLogService;

    @Value("${billing.serasa.pf-cost-per-query:15.52}")
    private BigDecimal pfCostPerQuery;

    /**
     * Consulta Serasa para o CPF e persiste o resultado.
     * Cada consulta cria um novo registro (histórico preservado).
     */
    public PersonAnalysis enrichBySerasa(String cpf) {
        String documentNumber = normalizeCpf(cpf);
        if (documentNumber == null || documentNumber.length() != 11) {
            throw new IllegalArgumentException("CPF deve conter 11 dígitos numéricos");
        }

        log.info("Consultando Serasa PF para CPF: {}", documentNumber);

        String rawJson = serasaPersonCreditRatingClient.consultarCreditRatingPF(documentNumber);
        PersonAnalysis analysis = serasaPersonCreditRatingMapper.toDomain(documentNumber, rawJson);
        PersonAnalysis saved = personAnalysisRepository.save(analysis);

        String currentUser = resolveCurrentUserName();
        apiUsageLogService.save(currentUser, saved.getPersonName(), documentNumber, "PF", pfCostPerQuery);

        return saved;
    }

    private String resolveCurrentUserName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserEntity user) {
            return user.getName();
        }
        return "Sistema";
    }

    /**
     * Processa um JSON bruto da Serasa PF sem chamar a API real (mock/dev).
     */
    public PersonAnalysis enrichBySerasaFromJson(String cpf, String rawJson) {
        String documentNumber = normalizeCpf(cpf);
        if (documentNumber == null || documentNumber.length() != 11) {
            throw new IllegalArgumentException("CPF deve conter 11 dígitos numéricos");
        }

        log.info("Enriquecendo Serasa PF (mock) para CPF: {}", documentNumber);

        PersonAnalysis analysis = serasaPersonCreditRatingMapper.toDomain(documentNumber, rawJson);
        return personAnalysisRepository.save(analysis);
    }

    /**
     * Retorna a análise mais recente para o CPF.
     */
    public PersonAnalysis getProfileByCpf(String cpf) {
        String documentNumber = normalizeCpf(cpf);
        return personAnalysisRepository.findLatestByCpf(documentNumber)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Nenhuma análise PF encontrada para CPF: " + documentNumber));
    }

    /**
     * Lista paginada de todas as análises PF, ordenadas pela consulta mais recente.
     */
    public Page<PersonAnalysis> findAllProfiles(Pageable pageable, String search) {
        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "consultaEm"));

        return (search != null && !search.isBlank())
                ? personAnalysisRepository.search(search.trim(), sorted)
                : personAnalysisRepository.findAll(sorted);
    }

    /**
     * Remove todas as análises do CPF.
     */
    @Transactional
    public void deleteProfile(String cpf) {
        String documentNumber = normalizeCpf(cpf);
        if (documentNumber == null) return;
        personAnalysisRepository.deleteByCpf(documentNumber);
    }

    public Optional<PersonAnalysis> findLatestByCpf(String cpf) {
        return personAnalysisRepository.findLatestByCpf(normalizeCpf(cpf));
    }

    public List<String> findRegisteredCompanyCnpjs(PersonAnalysis analysis) {
        if (analysis == null || analysis.getPartnerCompanies() == null || analysis.getPartnerCompanies().isNull()) {
            return List.of();
        }

        JsonNode companies = analysis.getPartnerCompanies().path("partnershipResponse");
        if (!companies.isArray() || companies.isEmpty()) {
            return List.of();
        }

        Set<String> cnpjs = new LinkedHashSet<>();
        for (JsonNode company : companies) {
            String cnpj = company.path("businessDocument").asText("");
            String digits = cnpj.replaceAll("\\D", "");
            if (digits.length() == 14) {
                cnpjs.add(digits);
            }
        }

        if (cnpjs.isEmpty()) {
            return List.of();
        }

        return new ArrayList<>(clientRepository.findExistingDocumentNumbers(cnpjs));
    }

    private String normalizeCpf(String cpf) {
        if (cpf == null || cpf.isBlank()) return null;
        String digits = cpf.replaceAll("\\D", "");
        return digits.length() >= 11 ? digits.substring(0, 11) : digits;
    }
}
