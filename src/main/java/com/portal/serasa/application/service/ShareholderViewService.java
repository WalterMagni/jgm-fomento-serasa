package com.portal.serasa.application.service;

import com.portal.serasa.api.rest.dto.response.RelatedCompanyResponse;
import com.portal.serasa.api.rest.dto.response.ShareholderResponse;
import com.portal.serasa.infrastructure.persistence.entity.ShareholderCompanyEntity;
import com.portal.serasa.infrastructure.persistence.entity.ShareholderEntity;
import com.portal.serasa.infrastructure.persistence.repository.CompanyDetailJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.CompanyPartnerJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ShareholderCompanyJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Monta a visão de quadro societário e grupo econômico para a API, cruzando os dados já
 * sincronizados por {@link CompanyShareholderService} com o cadastro do portal.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareholderViewService {

    private final CompanyShareholderService shareholderService;
    private final ShareholderCompanyJpaRepository shareholderCompanyRepository;
    private final CompanyDetailJpaRepository companyDetailRepository;
    private final CompanyPartnerJpaRepository companyPartnerRepository;

    public boolean isAvailable() {
        return shareholderService.isAvailable();
    }

    @Transactional
    public List<ShareholderResponse> getShareholders(String cnpj) {
        String raiz = root(cnpj);
        if (raiz == null) {
            return List.of();
        }
        List<ShareholderEntity> shareholders = shareholderService.syncRelatedCompanies(cnpj);
        Map<UUID, List<ShareholderCompanyEntity>> linksByShareholder = linksOf(shareholders);
        Map<UUID, ShareholderCompanyEntity> ownLink = ownLinks(raiz);

        return shareholders.stream()
                .map(s -> toShareholderResponse(s, ownLink.get(s.getId()),
                        countOthers(linksByShareholder.get(s.getId()), raiz)))
                .sorted(Comparator.comparing(ShareholderResponse::name))
                .toList();
    }

    /**
     * Empresas ligadas por sócio em comum. Entre um cedente e um sacado, um vínculo desses
     * é indício de título simulado; por isso a resposta destaca situação cadastral irregular
     * e se a empresa já está cadastrada no portal.
     */
    @Transactional
    public List<RelatedCompanyResponse> getRelatedCompanies(String cnpj) {
        String raiz = root(cnpj);
        if (raiz == null) {
            return List.of();
        }
        List<ShareholderEntity> shareholders = shareholderService.syncRelatedCompanies(cnpj);
        if (shareholders.isEmpty()) {
            return List.of();
        }
        Map<UUID, ShareholderEntity> byId = shareholders.stream()
                .collect(Collectors.toMap(ShareholderEntity::getId, s -> s, (a, b) -> a));

        // Agrupa os vínculos das outras empresas, acumulando quais sócios são compartilhados.
        Map<String, List<ShareholderCompanyEntity>> byRoot = new LinkedHashMap<>();
        for (ShareholderCompanyEntity link : shareholderCompanyRepository
                .findByShareholderIdIn(List.copyOf(byId.keySet()))) {
            if (!raiz.equals(link.getCnpjRaiz())) {
                byRoot.computeIfAbsent(link.getCnpjRaiz(), k -> new ArrayList<>()).add(link);
            }
        }
        if (byRoot.isEmpty()) {
            return List.of();
        }

        Map<String, String> registeredByRoot = registeredCnpjByRoot(byRoot.keySet());
        Set<String> linkedRoots = manuallyLinkedRoots(cnpj);

        return byRoot.entrySet().stream()
                .map(entry -> toRelatedResponse(entry.getKey(), entry.getValue(), byId,
                        registeredByRoot.get(entry.getKey()), linkedRoots.contains(entry.getKey())))
                // Irregular primeiro: empresa baixada com sócio em comum é o sinal mais forte.
                .sorted(Comparator.comparing(RelatedCompanyResponse::irregular).reversed()
                        .thenComparing(r -> r.companyName() == null ? "" : r.companyName()))
                .toList();
    }

    private ShareholderResponse toShareholderResponse(ShareholderEntity shareholder,
                                                      ShareholderCompanyEntity ownLink,
                                                      int otherCompaniesCount) {
        return ShareholderResponse.builder()
                .name(shareholder.getName())
                .document(shareholder.getDocument())
                .documentMask(shareholder.getDocumentMask())
                .documentType(shareholder.getDocumentType())
                .documentSource(shareholder.getDocumentSource())
                .qualification(ownLink == null ? null : ownLink.getQualificationDescription())
                .entryDate(ownLink == null ? null : ownLink.getEntryDate())
                .capitalPercent(ownLink == null ? null : ownLink.getCapitalPercent())
                .ageRange(shareholder.getAgeRange())
                .otherCompaniesCount(otherCompaniesCount)
                .build();
    }

    private RelatedCompanyResponse toRelatedResponse(String relatedRoot,
                                                     List<ShareholderCompanyEntity> links,
                                                     Map<UUID, ShareholderEntity> byId,
                                                     String registeredCnpj,
                                                     boolean alreadyLinked) {
        String status = links.stream()
                .map(ShareholderCompanyEntity::getCompanyStatus)
                .filter(s -> s != null)
                .findFirst().orElse(null);
        String receitaCnpj = links.stream()
                .map(ShareholderCompanyEntity::getCompanyCnpj)
                .filter(c -> c != null && !c.isBlank())
                .findFirst().orElse(null);
        String name = links.stream()
                .map(ShareholderCompanyEntity::getCompanyName)
                .filter(s -> s != null && !s.isBlank())
                .findFirst().orElse(null);

        List<RelatedCompanyResponse.SharedShareholder> shared = links.stream()
                .map(link -> {
                    ShareholderEntity s = byId.get(link.getShareholderId());
                    return s == null ? null : RelatedCompanyResponse.SharedShareholder.builder()
                            .name(s.getName())
                            .document(s.getDocument())
                            .documentMask(s.getDocumentMask())
                            .qualification(link.getQualificationDescription())
                            .entryDate(link.getEntryDate())
                            .build();
                })
                .filter(s -> s != null)
                .sorted(Comparator.comparing(RelatedCompanyResponse.SharedShareholder::name))
                .toList();

        return RelatedCompanyResponse.builder()
                .cnpjRaiz(relatedRoot)
                .cnpj(registeredCnpj)
                .receitaCnpj(receitaCnpj)
                .companyName(name)
                .companyStatus(status)
                .companyStatusLabel(statusLabel(status))
                .irregular(isIrregular(status))
                .inSystem(registeredCnpj != null)
                .alreadyLinked(alreadyLinked)
                .sharedShareholders(shared)
                .build();
    }

    private Map<UUID, List<ShareholderCompanyEntity>> linksOf(List<ShareholderEntity> shareholders) {
        if (shareholders.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = shareholders.stream().map(ShareholderEntity::getId).toList();
        return shareholderCompanyRepository.findByShareholderIdIn(ids).stream()
                .collect(Collectors.groupingBy(ShareholderCompanyEntity::getShareholderId));
    }

    private Map<UUID, ShareholderCompanyEntity> ownLinks(String raiz) {
        return shareholderCompanyRepository.findByCnpjRaiz(raiz).stream()
                .collect(Collectors.toMap(ShareholderCompanyEntity::getShareholderId, l -> l, (a, b) -> a));
    }

    private static int countOthers(List<ShareholderCompanyEntity> links, String raiz) {
        if (links == null) {
            return 0;
        }
        return (int) links.stream().filter(l -> !raiz.equals(l.getCnpjRaiz())).count();
    }

    /**
     * Raiz → CNPJ completo cadastrado no portal. A matriz ({@code 0001}) tem preferência, porque
     * é ela que o vínculo de empresa parceira espera receber.
     */
    private Map<String, String> registeredCnpjByRoot(Set<String> roots) {
        Map<String, String> byRoot = new LinkedHashMap<>();
        for (String document : companyDetailRepository.findDocumentNumbersByRootIn(roots)) {
            if (document == null || document.length() < 8) {
                continue;
            }
            String root = document.substring(0, 8);
            String current = byRoot.get(root);
            if (current == null || (!isHeadOffice(current) && isHeadOffice(document))) {
                byRoot.put(root, document);
            }
        }
        return byRoot;
    }

    private static boolean isHeadOffice(String cnpj) {
        return cnpj != null && cnpj.length() == 14 && "0001".equals(cnpj.substring(8, 12));
    }

    /** Raízes que já foram vinculadas à mão como empresa parceira (V51). */
    private Set<String> manuallyLinkedRoots(String cnpj) {
        String digits = CpfMask.digitsOnly(cnpj);
        if (digits == null) {
            return Set.of();
        }
        Set<String> linked = new HashSet<>();
        companyPartnerRepository.findAllByCnpj(digits).forEach(partner -> {
            addRoot(linked, partner.getCnpjA());
            addRoot(linked, partner.getCnpjB());
        });
        if (digits.length() >= 8) {
            linked.remove(digits.substring(0, 8));
        }
        return linked;
    }

    private static void addRoot(Set<String> target, String cnpj) {
        String digits = CpfMask.digitsOnly(cnpj);
        if (digits != null && digits.length() >= 8) {
            target.add(digits.substring(0, 8));
        }
    }

    private static boolean isIrregular(String status) {
        return status != null && !"02".equals(status) && !"2".equals(status);
    }

    /** Códigos de situação cadastral da Receita. */
    private static String statusLabel(String status) {
        if (status == null) {
            return null;
        }
        return switch (status.trim()) {
            case "01", "1" -> "Nula";
            case "02", "2" -> "Ativa";
            case "03", "3" -> "Suspensa";
            case "04", "4" -> "Inapta";
            case "08", "8" -> "Baixada";
            default -> status;
        };
    }

    private static String root(String cnpj) {
        String digits = CpfMask.digitsOnly(cnpj);
        return digits != null && digits.length() >= 8 ? digits.substring(0, 8) : null;
    }
}
