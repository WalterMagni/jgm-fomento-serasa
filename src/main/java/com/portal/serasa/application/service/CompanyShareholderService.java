package com.portal.serasa.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.portal.serasa.infrastructure.integration.cnpj.ShareholderQueryClient;
import com.portal.serasa.infrastructure.persistence.entity.ShareholderCompanyEntity;
import com.portal.serasa.infrastructure.persistence.entity.ShareholderEntity;
import com.portal.serasa.infrastructure.persistence.repository.ShareholderCompanyJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ShareholderJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Mantém o quadro societário (tabelas {@code shareholders} / {@code shareholder_companies})
 * sincronizado com a cópia local da Receita, com cache por TTL igual ao das filiais.
 *
 * <p>Dois níveis de profundidade:</p>
 * <ol>
 *   <li>{@link #syncShareholders(String)} — sócios da empresa. Uma consulta.</li>
 *   <li>{@link #syncRelatedCompanies(String)} — para cada sócio, todas as outras empresas dele.
 *       É o segundo salto que revela o grupo econômico de fato. Uma consulta por sócio.</li>
 * </ol>
 *
 * <p>A identidade do sócio é {@code (documentMask, name)} enquanto só existir a fonte gratuita.
 * Quando o CPF completo chega pelo QSA do Serasa, {@link #applyFullDocument} faz upgrade da
 * linha existente em vez de criar um sócio duplicado — ver {@link CpfMask}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyShareholderService {

    private final ShareholderQueryClient shareholderClient;
    private final SerasaQsaExtractor qsaExtractor;
    private final ShareholderJpaRepository shareholderRepository;
    private final ShareholderCompanyJpaRepository shareholderCompanyRepository;

    @Value("${cnpj.shareholders.cache-ttl-days:30}")
    private int cacheTtlDays;

    public boolean isAvailable() {
        return shareholderClient.isAvailable();
    }

    /** Sócios da empresa, recarregando da Receita quando o cache estiver vencido. */
    @Transactional
    public List<ShareholderEntity> syncShareholders(String cnpj) {
        String raiz = extractRaiz(cnpj);
        if (raiz == null) {
            return List.of();
        }
        if (isCacheFresh(raiz)) {
            return loadShareholdersOf(raiz);
        }
        requireAvailable();

        LocalDateTime now = LocalDateTime.now();
        List<ShareholderEntity> result = new ArrayList<>();
        for (ShareholderQueryClient.ShareholderRow row : shareholderClient.fetchShareholders(raiz)) {
            if (row.name() == null || row.documentMask() == null) {
                continue;
            }
            ShareholderEntity shareholder = upsertShareholder(row, now);
            upsertLink(shareholder.getId(), raiz, row.companyName(), row.qualificationCode(),
                    row.qualificationDescription(), row.entryDate(), row.companyStatus(),
                    row.companyCnpj(), ShareholderCompanyEntity.SOURCE_RECEITA, now);
            result.add(shareholder);
        }
        log.info("Quadro societario raiz={} sincronizado: {} socios", raiz, result.size());
        return result;
    }

    /**
     * Para cada sócio da empresa, busca as demais empresas dele e persiste os vínculos.
     * É o dado que consultas pagas vendem como "empresas vinculadas ao CPF".
     */
    @Transactional
    public List<ShareholderEntity> syncRelatedCompanies(String cnpj) {
        List<ShareholderEntity> shareholders = syncShareholders(cnpj);
        if (shareholders.isEmpty()) {
            return shareholders;
        }
        requireAvailable();

        String raiz = extractRaiz(cnpj);
        LocalDateTime now = LocalDateTime.now();
        for (ShareholderEntity shareholder : shareholders) {
            List<ShareholderQueryClient.ShareholderCompanyRow> companies =
                    shareholderClient.fetchCompaniesOfShareholder(shareholder.getDocumentMask(), shareholder.getName());
            for (ShareholderQueryClient.ShareholderCompanyRow row : companies) {
                if (row.cnpjRaiz() == null || row.cnpjRaiz().equals(raiz)) {
                    continue;
                }
                upsertLink(shareholder.getId(), row.cnpjRaiz(), row.companyName(), row.qualificationCode(),
                        row.qualificationDescription(), row.entryDate(), row.companyStatus(),
                        row.companyCnpj(), ShareholderCompanyEntity.SOURCE_RECEITA, now);
            }
            log.debug("Socio {} vinculado a {} outras empresas", shareholder.getName(), companies.size());
        }
        return shareholders;
    }

    /**
     * Aplica o CPF completo vindo de fonte paga (QSA do Serasa) sobre o sócio já conhecido
     * pela máscara. Se ninguém casar, cria o sócio já com o documento completo.
     *
     * <p>É o que destrava a consulta de processos e restritivos por pessoa em serviços
     * externos, que não aceitam CPF mascarado.</p>
     */
    @Transactional
    public Optional<ShareholderEntity> applyFullDocument(String fullCpf, String name) {
        String digits = CpfMask.digitsOnly(fullCpf);
        String mask = CpfMask.fromCpf(digits);
        if (digits == null || mask == null || name == null || name.isBlank()) {
            return Optional.empty();
        }
        String cleanName = name.trim();

        // 1) O CPF completo já é conhecido — nada a fazer além de manter o nome.
        Optional<ShareholderEntity> byDocument = shareholderRepository.findByDocument(digits);
        if (byDocument.isPresent()) {
            return byDocument;
        }

        // 2) Existe a linha da carga gratuita com a mesma máscara e nome: faz upgrade in-place.
        Optional<ShareholderEntity> byMask = shareholderRepository.findByDocumentMaskAndName(mask, cleanName);
        if (byMask.isPresent()) {
            ShareholderEntity shareholder = byMask.get();
            shareholder.setDocument(digits);
            shareholder.setDocumentSource(ShareholderEntity.SOURCE_SERASA);
            shareholder.setUpdatedAt(LocalDateTime.now());
            log.info("CPF completo aplicado ao socio {} (mascara {})", cleanName, mask);
            return Optional.of(shareholderRepository.save(shareholder));
        }

        // 3) Sócio que a Receita ainda não trouxe (carga defasada ou entrada recente).
        LocalDateTime now = LocalDateTime.now();
        return Optional.of(shareholderRepository.save(ShareholderEntity.builder()
                .documentType(ShareholderEntity.TYPE_CPF)
                .document(digits)
                .documentMask(mask)
                .name(cleanName)
                .documentSource(ShareholderEntity.SOURCE_SERASA)
                .createdAt(now)
                .updatedAt(now)
                .build()));
    }

    /**
     * Importa o QSA do relatório da Serasa, que traz o <b>CPF completo</b> de cada sócio.
     * Cada sócio é casado com a linha já criada pela carga gratuita (máscara + nome) e recebe
     * o documento completo; o vínculo com a empresa ganha o percentual de capital e o status,
     * que a Receita não publica.
     *
     * <p>Só o vínculo com a empresa consultada é escrito — as demais empresas do sócio
     * continuam vindo da Receita, via {@link #syncRelatedCompanies(String)}.</p>
     *
     * @return quantidade de sócios importados.
     */
    @Transactional
    public int importSerasaQsa(String cnpj, JsonNode partnerDetails) {
        String raiz = extractRaiz(cnpj);
        if (raiz == null) {
            return 0;
        }
        List<SerasaQsaExtractor.QsaPartner> partners = qsaExtractor.extract(partnerDetails);
        if (partners.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        int imported = 0;
        for (SerasaQsaExtractor.QsaPartner partner : partners) {
            Optional<ShareholderEntity> shareholder = applyFullDocument(partner.cpf(), partner.name());
            if (shareholder.isEmpty()) {
                continue;
            }
            ShareholderCompanyEntity link = findOrNewLink(shareholder.get().getId(), raiz);
            if (partner.role() != null) {
                link.setQualificationDescription(partner.role());
            }
            link.setPartnerStatus(partner.status());
            link.setCapitalPercent(partner.capitalPercent());
            if (link.getEntryDate() == null) {
                link.setEntryDate(partner.sinceDate());
            }
            link.setSource(ShareholderCompanyEntity.SOURCE_SERASA);
            link.setFetchedAt(now);
            shareholderCompanyRepository.save(link);
            imported++;
        }
        log.info("QSA Serasa importado para raiz={}: {} socios com CPF completo", raiz, imported);
        return imported;
    }

    /** Raízes de CNPJ que compartilham ao menos um sócio com a informada. */
    @Transactional(readOnly = true)
    public List<String> findRelatedRoots(String cnpj) {
        String raiz = extractRaiz(cnpj);
        return raiz == null ? List.of() : shareholderCompanyRepository.findRelatedRoots(raiz);
    }

    /**
     * {@code true} se as duas empresas compartilham sócio. Entre cedente e sacado de um
     * mesmo título isso é indício de duplicata simulada.
     */
    @Transactional(readOnly = true)
    public boolean shareShareholder(String cnpjA, String cnpjB) {
        return !findSharedShareholders(cnpjA, cnpjB).isEmpty();
    }

    /**
     * Nomes dos sócios que as duas empresas têm em comum, em ordem alfabética.
     *
     * <p>Lê apenas as tabelas locais já sincronizadas — não consulta a Receita. Quem chama em
     * volume (a análise de praça de pagamento) precisa que isto seja barato; garantir que o
     * quadro societário está carregado é responsabilidade de quem sincroniza.</p>
     *
     * @return lista vazia quando não há sócio em comum, quando é a mesma empresa, ou quando
     *         alguma das duas ainda não teve o quadro societário sincronizado.
     */
    @Transactional(readOnly = true)
    public List<String> findSharedShareholders(String cnpjA, String cnpjB) {
        String raizA = extractRaiz(cnpjA);
        String raizB = extractRaiz(cnpjB);
        if (raizA == null || raizB == null || raizA.equals(raizB)) {
            return List.of();
        }
        Set<java.util.UUID> idsB = shareholderCompanyRepository.findByCnpjRaiz(raizB).stream()
                .map(ShareholderCompanyEntity::getShareholderId)
                .collect(java.util.stream.Collectors.toSet());
        if (idsB.isEmpty()) {
            return List.of();
        }
        List<java.util.UUID> shared = shareholderCompanyRepository.findByCnpjRaiz(raizA).stream()
                .map(ShareholderCompanyEntity::getShareholderId)
                .filter(idsB::contains)
                .distinct()
                .toList();
        if (shared.isEmpty()) {
            return List.of();
        }
        return shareholderRepository.findAllById(shared).stream()
                .map(ShareholderEntity::getName)
                .sorted()
                .toList();
    }

    private ShareholderEntity upsertShareholder(ShareholderQueryClient.ShareholderRow row, LocalDateTime now) {
        String name = row.name().trim();
        return shareholderRepository.findByDocumentMaskAndName(row.documentMask(), name)
                .map(existing -> {
                    if (existing.getAgeRange() == null && row.ageRange() != null) {
                        existing.setAgeRange(row.ageRange());
                        existing.setUpdatedAt(now);
                        return shareholderRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> shareholderRepository.save(ShareholderEntity.builder()
                        .documentType(row.documentType())
                        // Sócio pessoa jurídica traz o CNPJ completo, então já nasce sem máscara.
                        .document(ShareholderEntity.TYPE_CNPJ.equals(row.documentType()) ? row.documentMask() : null)
                        .documentMask(row.documentMask())
                        .name(name)
                        .documentSource(ShareholderEntity.SOURCE_RECEITA_MASK)
                        .ageRange(row.ageRange())
                        .createdAt(now)
                        .updatedAt(now)
                        .build()));
    }

    private ShareholderCompanyEntity findOrNewLink(java.util.UUID shareholderId, String cnpjRaiz) {
        return shareholderCompanyRepository.findByShareholderIdAndCnpjRaiz(shareholderId, cnpjRaiz)
                .orElseGet(() -> ShareholderCompanyEntity.builder()
                        .shareholderId(shareholderId)
                        .cnpjRaiz(cnpjRaiz)
                        .build());
    }

    private void upsertLink(java.util.UUID shareholderId, String cnpjRaiz, String companyName,
                            String qualificationCode, String qualificationDescription,
                            java.time.LocalDate entryDate, String companyStatus,
                            String companyCnpj, String source, LocalDateTime now) {
        ShareholderCompanyEntity link = findOrNewLink(shareholderId, cnpjRaiz);
        if (companyName != null) {
            link.setCompanyName(companyName);
        }
        link.setQualificationCode(qualificationCode);
        link.setQualificationDescription(qualificationDescription);
        link.setEntryDate(entryDate);
        if (companyStatus != null) {
            link.setCompanyStatus(companyStatus);
        }
        if (companyCnpj != null) {
            link.setCompanyCnpj(companyCnpj);
        }
        // A carga da Receita nao rebaixa um vinculo que ja veio do Serasa: o dado pago tem
        // percentual de capital e status que a base gratuita nao publica.
        if (link.getSource() == null || !ShareholderCompanyEntity.SOURCE_SERASA.equals(link.getSource())) {
            link.setSource(source);
        }
        link.setFetchedAt(now);
        shareholderCompanyRepository.save(link);
    }

    private List<ShareholderEntity> loadShareholdersOf(String raiz) {
        List<java.util.UUID> ids = shareholderCompanyRepository.findByCnpjRaiz(raiz).stream()
                .map(ShareholderCompanyEntity::getShareholderId).distinct().toList();
        return ids.isEmpty() ? List.of() : shareholderRepository.findAllById(ids);
    }

    private boolean isCacheFresh(String raiz) {
        Optional<LocalDateTime> last = shareholderCompanyRepository.findLastFetchedAt(raiz);
        return last.isPresent() && last.get().isAfter(LocalDateTime.now().minusDays(cacheTtlDays));
    }

    private void requireAvailable() {
        if (!shareholderClient.isAvailable()) {
            throw new IllegalStateException("Consulta de quadro societario indisponivel (cnpj.datasource.enabled=false)");
        }
    }

    /** Extrai a raiz (8 primeiros dígitos) de um CNPJ em qualquer formato. */
    private static String extractRaiz(String cnpj) {
        String digits = CpfMask.digitsOnly(cnpj);
        return digits != null && digits.length() >= 8 ? digits.substring(0, 8) : null;
    }
}
