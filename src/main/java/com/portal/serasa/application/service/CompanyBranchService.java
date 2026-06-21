package com.portal.serasa.application.service;

import com.portal.serasa.api.rest.dto.response.CompanyBranchResponse;
import com.portal.serasa.application.port.out.CompanyDetailRepository;
import com.portal.serasa.domain.model.CompanyDetail;
import com.portal.serasa.infrastructure.integration.bigquery.CompanyBranchClient;
import com.portal.serasa.infrastructure.persistence.entity.CompanyBranchEntity;
import com.portal.serasa.infrastructure.persistence.repository.CompanyBranchJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolve as filiais (estabelecimentos da raiz do CNPJ) com cache em Postgres.
 * Busca no BigQuery só quando o cache está vazio ou vencido (TTL); geocodifica
 * cada estabelecimento pelo centroide do município, usando a coordenada precisa
 * de company_details quando existir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyBranchService {

    private final CompanyBranchClient branchClient;
    private final CompanyBranchJpaRepository branchRepository;
    private final CompanyDetailRepository companyDetailRepository;
    private final MunicipalityGeocoder geocoder;

    @Value("${bigquery.branches.cache-ttl-days:30}")
    private int cacheTtlDays;

    public boolean isAvailable() {
        return branchClient.isAvailable();
    }

    @Transactional
    public List<CompanyBranchResponse> getBranches(String cnpj) {
        String raiz = extractRaiz(cnpj);
        if (raiz == null) {
            return List.of();
        }
        if (isCacheFresh(raiz)) {
            return toResponses(branchRepository.findByCnpjRaiz(raiz));
        }
        if (!branchClient.isAvailable()) {
            throw new IllegalStateException("Consulta de filiais indisponível (BigQuery desabilitado)");
        }
        List<CompanyBranchClient.BranchRow> rows = branchClient.fetchBranches(raiz);
        List<CompanyBranchEntity> entities = geocodeAndBuild(raiz, rows);
        branchRepository.deleteByCnpjRaiz(raiz);
        branchRepository.saveAll(entities);
        return toResponses(entities);
    }

    /** Mapeia para DTO marcando quais CNPJs já têm perfil em company_details. */
    private List<CompanyBranchResponse> toResponses(List<CompanyBranchEntity> entities) {
        List<String> cnpjs = entities.stream().map(CompanyBranchEntity::getCnpj).filter(c -> c != null).toList();
        var existing = cnpjs.isEmpty()
                ? java.util.Set.<String>of()
                : companyDetailRepository.findByDocumentNumberIn(cnpjs).stream()
                .map(CompanyDetail::getDocumentNumber).collect(Collectors.toSet());
        return entities.stream().map(e -> toResponse(e, existing.contains(e.getCnpj()))).toList();
    }

    private boolean isCacheFresh(String raiz) {
        Optional<LocalDateTime> last = branchRepository.findLastFetchedAt(raiz);
        return last.isPresent() && last.get().isAfter(LocalDateTime.now().minusDays(cacheTtlDays));
    }

    private List<CompanyBranchEntity> geocodeAndBuild(String raiz, List<CompanyBranchClient.BranchRow> rows) {
        // Coordenadas precisas (street-level) de quem já está enriquecido em company_details.
        List<String> cnpjs = rows.stream().map(CompanyBranchClient.BranchRow::cnpj).filter(c -> c != null).toList();
        Map<String, CompanyDetail> precise = cnpjs.isEmpty()
                ? Map.of()
                : companyDetailRepository.findByDocumentNumberIn(cnpjs).stream()
                .collect(Collectors.toMap(CompanyDetail::getDocumentNumber, Function.identity(), (a, b) -> a));

        LocalDateTime now = LocalDateTime.now();
        return rows.stream().map(row -> {
            BigDecimal lat = null;
            BigDecimal lng = null;
            CompanyDetail detail = row.cnpj() == null ? null : precise.get(row.cnpj());
            if (detail != null && detail.getLatitude() != null && detail.getLongitude() != null) {
                lat = BigDecimal.valueOf(detail.getLatitude());
                lng = BigDecimal.valueOf(detail.getLongitude());
            } else if (row.nomeMunicipio() != null && row.uf() != null) {
                Optional<MunicipalityGeocoder.Coordinates> coords =
                        geocoder.resolve(row.nomeMunicipio() + "/" + row.uf());
                if (coords.isPresent()) {
                    lat = coords.get().latitude();
                    lng = coords.get().longitude();
                }
            }
            return CompanyBranchEntity.builder()
                    .cnpjRaiz(raiz)
                    .cnpj(row.cnpj())
                    .matrizFilial(row.matrizFilial())
                    .nomeFantasia(blankToNull(row.nomeFantasia()))
                    .uf(row.uf())
                    .idMunicipio(row.idMunicipio())
                    .nomeMunicipio(row.nomeMunicipio())
                    .logradouro(blankToNull(row.logradouro()))
                    .numero(blankToNull(row.numero()))
                    .bairro(blankToNull(row.bairro()))
                    .cep(blankToNull(row.cep()))
                    .latitude(lat)
                    .longitude(lng)
                    .fetchedAt(now)
                    .build();
        }).toList();
    }

    private CompanyBranchResponse toResponse(CompanyBranchEntity e, boolean inSystem) {
        return CompanyBranchResponse.builder()
                .cnpj(e.getCnpj())
                .matriz("1".equals(e.getMatrizFilial()))
                .inSystem(inSystem)
                .nomeFantasia(e.getNomeFantasia())
                .uf(e.getUf())
                .municipio(e.getNomeMunicipio())
                .address(buildAddress(e))
                .latitude(e.getLatitude())
                .longitude(e.getLongitude())
                .build();
    }

    private static String buildAddress(CompanyBranchEntity e) {
        StringBuilder sb = new StringBuilder();
        if (e.getLogradouro() != null) {
            sb.append(e.getLogradouro());
            if (e.getNumero() != null) {
                sb.append(", ").append(e.getNumero());
            }
        }
        if (e.getBairro() != null) {
            if (sb.length() > 0) {
                sb.append(" - ");
            }
            sb.append(e.getBairro());
        }
        if (e.getNomeMunicipio() != null) {
            if (sb.length() > 0) {
                sb.append(" - ");
            }
            sb.append(e.getNomeMunicipio());
            if (e.getUf() != null) {
                sb.append("/").append(e.getUf());
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Extrai a raiz (8 primeiros dígitos) de um CNPJ em qualquer formato. */
    private static String extractRaiz(String cnpj) {
        if (cnpj == null) {
            return null;
        }
        String digits = cnpj.replaceAll("\\D", "");
        return digits.length() >= 8 ? digits.substring(0, 8) : null;
    }
}
