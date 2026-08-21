package com.portal.serasa.application.service;

import com.portal.serasa.api.rest.dto.response.CompanyPartnerResponse;
import com.portal.serasa.api.rest.dto.response.CompanySearchItemResponse;
import com.portal.serasa.application.port.out.CompanyDetailRepository;
import com.portal.serasa.domain.model.CompanyDetail;
import com.portal.serasa.infrastructure.persistence.entity.CompanyPartnerEntity;
import com.portal.serasa.infrastructure.persistence.repository.CompanyPartnerJpaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Vínculos manuais de empresas parceiras (mesmo grupo, CNPJs distintos).
 * O par é guardado uma única vez em ordem canônica, então o vínculo aparece
 * automaticamente nos dois perfis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyPartnerService {

    private static final int SEARCH_LIMIT = 20;

    private final CompanyPartnerJpaRepository partnerRepository;
    private final CompanyDetailRepository companyDetailRepository;

    @Transactional(readOnly = true)
    public List<CompanyPartnerResponse> listPartners(String cnpj) {
        String normalized = normalize(cnpj);
        List<CompanyPartnerEntity> pairs = partnerRepository.findAllByCnpj(normalized);
        if (pairs.isEmpty()) {
            return List.of();
        }
        List<String> otherCnpjs = pairs.stream().map(p -> otherSide(p, normalized)).toList();
        Map<String, CompanyDetail> details = companyDetailRepository.findByDocumentNumberIn(otherCnpjs).stream()
                .collect(Collectors.toMap(CompanyDetail::getDocumentNumber, Function.identity(), (a, b) -> a));

        return pairs.stream()
                .map(pair -> toResponse(pair, otherSide(pair, normalized), details))
                .sorted(Comparator.comparing(
                        r -> r.getCompanyName() == null ? "" : r.getCompanyName(),
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public CompanyPartnerResponse addPartner(String cnpj, String partnerCnpj, String note, String authorName) {
        String a = normalize(cnpj);
        String b = normalize(partnerCnpj);
        if (a.equals(b)) {
            throw new IllegalArgumentException("Uma empresa não pode ser parceira dela mesma");
        }
        if (!companyDetailRepository.existsByDocumentNumber(a)) {
            throw new EntityNotFoundException("Empresa não encontrada para CNPJ: " + a);
        }
        if (!companyDetailRepository.existsByDocumentNumber(b)) {
            throw new EntityNotFoundException("Empresa parceira não tem perfil cadastrado: " + b);
        }

        String first = a.compareTo(b) < 0 ? a : b;
        String second = a.compareTo(b) < 0 ? b : a;
        LocalDateTime now = LocalDateTime.now();

        CompanyPartnerEntity entity = partnerRepository.findByCnpjAAndCnpjB(first, second)
                .orElseGet(() -> CompanyPartnerEntity.builder()
                        .cnpjA(first)
                        .cnpjB(second)
                        .authorName(authorName)
                        .createdAt(now)
                        .build());
        if (note != null && !note.isBlank()) {
            entity.setNote(note.trim());
        }
        entity.setUpdatedAt(now);
        CompanyPartnerEntity saved = partnerRepository.save(entity);

        String other = otherSide(saved, a);
        Map<String, CompanyDetail> details = companyDetailRepository.findByDocumentNumberIn(List.of(other)).stream()
                .collect(Collectors.toMap(CompanyDetail::getDocumentNumber, Function.identity(), (x, y) -> x));
        return toResponse(saved, other, details);
    }

    @Transactional
    public CompanyPartnerResponse updateNote(String cnpj, String partnerCnpj, String note) {
        String a = normalize(cnpj);
        String b = normalize(partnerCnpj);
        CompanyPartnerEntity entity = findPair(a, b);
        entity.setNote(note == null || note.isBlank() ? null : note.trim());
        entity.setUpdatedAt(LocalDateTime.now());
        CompanyPartnerEntity saved = partnerRepository.save(entity);

        Map<String, CompanyDetail> details = companyDetailRepository.findByDocumentNumberIn(List.of(b)).stream()
                .collect(Collectors.toMap(CompanyDetail::getDocumentNumber, Function.identity(), (x, y) -> x));
        return toResponse(saved, b, details);
    }

    /** Remove o vínculo — some dos dois perfis, já que existe uma linha só. */
    @Transactional
    public void removePartner(String cnpj, String partnerCnpj) {
        partnerRepository.delete(findPair(normalize(cnpj), normalize(partnerCnpj)));
    }

    /**
     * Busca empresas já cadastradas para escolher como parceira, marcando as que já
     * estão vinculadas e escondendo a própria empresa de referência.
     */
    @Transactional(readOnly = true)
    public List<CompanySearchItemResponse> searchCandidates(String cnpj, String term) {
        String normalized = normalize(cnpj);
        Set<String> alreadyPartners = partnerRepository.findAllByCnpj(normalized).stream()
                .map(p -> otherSide(p, normalized))
                .collect(Collectors.toSet());

        return companyDetailRepository.searchByNameOrDocument(term, SEARCH_LIMIT).stream()
                .filter(detail -> !normalized.equals(detail.getDocumentNumber()))
                .map(detail -> CompanySearchItemResponse.builder()
                        .cnpj(detail.getDocumentNumber())
                        .companyName(detail.getCompanyName())
                        .alias(detail.getAlias())
                        .address(address(detail))
                        .city(detail.getCity())
                        .state(detail.getState())
                        .alreadyPartner(alreadyPartners.contains(detail.getDocumentNumber()))
                        .build())
                .toList();
    }

    private CompanyPartnerEntity findPair(String a, String b) {
        String first = a.compareTo(b) < 0 ? a : b;
        String second = a.compareTo(b) < 0 ? b : a;
        return partnerRepository.findByCnpjAAndCnpjB(first, second)
                .orElseThrow(() -> new EntityNotFoundException("Vínculo de parceria não encontrado"));
    }

    private CompanyPartnerResponse toResponse(CompanyPartnerEntity pair, String otherCnpj,
                                              Map<String, CompanyDetail> details) {
        CompanyDetail detail = details.get(otherCnpj);
        return CompanyPartnerResponse.builder()
                .id(pair.getId())
                .cnpj(otherCnpj)
                .companyName(detail == null ? null : detail.getCompanyName())
                .alias(detail == null ? null : detail.getAlias())
                .address(detail == null ? null : address(detail))
                .city(detail == null ? null : detail.getCity())
                .state(detail == null ? null : detail.getState())
                .note(pair.getNote())
                .authorName(pair.getAuthorName())
                .createdAt(pair.getCreatedAt())
                .build();
    }

    private String address(CompanyDetail detail) {
        return AddressFormat.format(detail.getStreet(), detail.getNumber(), detail.getDetails(),
                detail.getDistrict(), detail.getCity(), detail.getState(), detail.getZip());
    }

    private String otherSide(CompanyPartnerEntity pair, String cnpj) {
        return cnpj.equals(pair.getCnpjA()) ? pair.getCnpjB() : pair.getCnpjA();
    }

    private String normalize(String cnpj) {
        return cnpj == null ? "" : cnpj.replaceAll("\\D", "");
    }
}
