package com.portal.serasa.application.service;

import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.domain.model.CompanyDetail;
import com.portal.serasa.infrastructure.persistence.entity.PaymentPlaceBatchEntity;
import com.portal.serasa.infrastructure.persistence.entity.PaymentPlaceEntryEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.PaymentPlaceBatchJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.PaymentPlaceEntryJpaRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentPlaceAnalysisService {

    private static final String STATUS_IMPORTED = "IMPORTADO";
    private static final String STATUS_ARCHIVED = "ARQUIVADO";
    private static final String ENTRY_STATUS_PENDING = "ANALISE_PENDENTE";
    private static final String ENTRY_STATUS_REVIEWED = "ANALISE_CONCLUIDA";

    private final PaymentPlacePdfParser parser;
    private final PaymentPlaceInstitutionClassifier institutionClassifier;
    private final MunicipalityGeocoder geocoder;
    private final PaymentPlaceScorer scorer;
    private final PaymentPlaceAgencyEnricher agencyEnricher;
    private final PaymentPlaceSacadoEnricher sacadoEnricher;
    private final ClientProfileService clientProfileService;
    private final com.portal.serasa.infrastructure.integration.gemini.GeminiAiService geminiAiService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final com.portal.serasa.application.port.out.ClientRepository clientRepository;
    private final com.portal.serasa.application.port.out.CompanyDetailRepository companyDetailRepository;
    private final PaymentPlaceBatchJpaRepository batchRepository;
    private final PaymentPlaceEntryJpaRepository entryRepository;

    @Transactional
    public PaymentPlaceImportResult importPdf(String fileName, InputStream inputStream, UserEntity currentUser)
            throws IOException {
        List<PaymentPlacePdfParser.PaymentPlaceParsedEntry> parsedEntries = parser.parse(inputStream);

        long auditEntries = parsedEntries.stream()
                .filter(entry -> PaymentPlacePdfParser.SECTION_AUDIT.equals(entry.getSection()))
                .count();
        long unlocatedAgencyEntries = parsedEntries.stream()
                .filter(entry -> PaymentPlacePdfParser.SECTION_UNLOCATED_AGENCIES.equals(entry.getSection()))
                .count();

        PaymentPlaceBatchEntity batch = PaymentPlaceBatchEntity.builder()
                .fileName(fileName)
                .importedByUserId(currentUser != null ? currentUser.getId() : null)
                .importedByName(currentUser != null ? currentUser.getName() : null)
                .importedByEmail(currentUser != null ? currentUser.getEmail() : null)
                .importedAt(LocalDateTime.now())
                .status(STATUS_IMPORTED)
                .totalEntries(parsedEntries.size())
                .auditEntries((int) auditEntries)
                .unlocatedAgencyEntries((int) unlocatedAgencyEntries)
                .build();
        batch = batchRepository.save(batch);

        UUID batchId = batch.getId();
        List<PaymentPlaceEntryEntity> entries = parsedEntries.stream()
                .map(entry -> toEntity(batchId, entry))
                .toList();
        entryRepository.saveAll(entries);

        // Enriquecimento da agência via Bacen roda em background após o commit,
        // para o import responder rápido (o usuário vê os endereços ao atualizar).
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        agencyEnricher.enrichBatch(batchId);
                    }
                });

        return PaymentPlaceImportResult.builder()
                .batch(batch)
                .entries(entries)
                .build();
    }

    @Transactional(readOnly = true)
    public List<PaymentPlaceBatchEntity> listBatches() {
        return batchRepository.findAllByOrderByImportedAtDesc();
    }

    @Transactional(readOnly = true)
    public PaymentPlaceBatchWithEntries getBatch(UUID batchId) {
        PaymentPlaceBatchEntity batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new EntityNotFoundException("Lote de praça de pagamento não encontrado"));
        List<PaymentPlaceEntryEntity> entries = entryRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
        return PaymentPlaceBatchWithEntries.builder()
                .batch(batch)
                .entries(entries)
                .build();
    }

    @Transactional(readOnly = true)
    public PaymentPlaceBatchIndicators getBatchIndicators(UUID batchId) {
        PaymentPlaceBatchEntity batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new EntityNotFoundException("Lote de praça de pagamento não encontrado"));
        List<PaymentPlaceEntryEntity> entries = entryRepository.findByBatchIdOrderByCreatedAtAsc(batchId);

        int totalEntries = entries.size();
        int locatedAgencyCount = (int) entries.stream()
                .filter(this::hasResolvedAgencyLocation)
                .count();
        int lowReliabilityCount = (int) entries.stream()
                .filter(entry -> "BAIXA".equals(entry.getGeographicReliability()))
                .count();

        List<PaymentPlaceEntryEntity> comparableEntries = entries.stream()
                .filter(entry -> entry.getAnalystDecision() != null)
                .filter(entry -> mapSuggestionToDecision(entry.getAutomaticSuggestion()) != null)
                .toList();
        int comparableDecisionCount = comparableEntries.size();
        int agreementCount = (int) comparableEntries.stream()
                .filter(entry -> entry.getAnalystDecision().equals(mapSuggestionToDecision(entry.getAutomaticSuggestion())))
                .count();
        int disagreementCount = comparableDecisionCount - agreementCount;

        Map<BankAgencyKey, List<PaymentPlaceEntryEntity>> groups = entries.stream()
                .collect(Collectors.groupingBy(this::toBankAgencyKey));

        List<BankAgencyIndicator> rankedIndicators = groups.entrySet().stream()
                .map(entry -> toBankAgencyIndicator(entry.getKey(), entry.getValue()))
                .toList();

        Comparator<BankAgencyIndicator> recurringComparator = Comparator
                .comparingInt(BankAgencyIndicator::totalEntries).reversed()
                .thenComparingInt(BankAgencyIndicator::disagreementCount).reversed()
                .thenComparing(BankAgencyIndicator::bankAgency);

        Comparator<BankAgencyIndicator> divergentComparator = Comparator
                .comparingInt(BankAgencyIndicator::disagreementCount).reversed()
                .thenComparing(BankAgencyIndicator::disagreementPct).reversed()
                .thenComparingInt(BankAgencyIndicator::totalEntries).reversed()
                .thenComparing(BankAgencyIndicator::bankAgency);

        return new PaymentPlaceBatchIndicators(
                batch,
                totalEntries,
                locatedAgencyCount,
                percentage(locatedAgencyCount, totalEntries),
                lowReliabilityCount,
                percentage(lowReliabilityCount, totalEntries),
                comparableDecisionCount,
                agreementCount,
                percentage(agreementCount, comparableDecisionCount),
                disagreementCount,
                percentage(disagreementCount, comparableDecisionCount),
                rankedIndicators.stream()
                        .sorted(recurringComparator)
                        .limit(5)
                        .toList(),
                rankedIndicators.stream()
                        .filter(indicator -> indicator.disagreementCount() > 0)
                        .sorted(divergentComparator)
                        .limit(5)
                        .toList()
        );
    }

    @Transactional
    public PaymentPlaceEntryEntity decideEntry(UUID entryId, String decision, String notes, UserEntity user) {
        PaymentPlaceEntryEntity entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new EntityNotFoundException("Lançamento de praça de pagamento não encontrado"));

        String normalizedDecision = normalizeDecision(decision);
        entry.setAnalystDecision(normalizedDecision);
        entry.setAnalystNotes(clean(notes));
        entry.setAnalysisStatus(ENTRY_STATUS_REVIEWED);
        entry.setReopenedAt(null);
        stampDecision(entry, user);
        PaymentPlaceEntryEntity saved = entryRepository.save(entry);
        if ("CEDENTE".equals(normalizedDecision)) {
            scheduleSacadoProfile(saved.getPayerDocument());
        }
        return saved;
    }

    /**
     * Após o commit da decisão, garante (em background) o perfil da empresa do sacado.
     * Quando o título é CEDENTE, o valor passa a aparecer também na Visão Cedente do sacado —
     * então ele precisa existir como empresa no sistema (busca padrão CNPJ Já; Serasa fica manual).
     */
    private void scheduleSacadoProfile(String payerDocument) {
        if (payerDocument == null || payerDocument.isBlank()) {
            return;
        }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        sacadoEnricher.ensureProfile(payerDocument);
                    }
                });
    }

    /**
     * Desfaz a decisão de um lançamento (acionado ao "apagar" a análise na página do
     * cliente): volta para PENDENTE, limpa a autoria da decisão, marca como reaberto
     * (para destaque na triagem) e desarquiva o lote se necessário — assim o lançamento
     * reaparece na Praça de Pagamento mesmo que o lote do dia já tenha sido arquivado.
     */
    @Transactional
    public PaymentPlaceEntryEntity reopenEntry(UUID entryId) {
        PaymentPlaceEntryEntity entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new EntityNotFoundException("Lançamento de praça de pagamento não encontrado"));

        entry.setAnalystDecision(null);
        entry.setAnalysisStatus(ENTRY_STATUS_PENDING);
        entry.setDecidedAt(null);
        entry.setDecidedByUserId(null);
        entry.setDecidedByName(null);
        entry.setReopenedAt(LocalDateTime.now());

        batchRepository.findById(entry.getBatchId()).ifPresent(batch -> {
            if (STATUS_ARCHIVED.equals(batch.getStatus())) {
                batch.setStatus(STATUS_IMPORTED);
                batchRepository.save(batch);
            }
        });

        return entryRepository.save(entry);
    }

    /** Registra quem decidiu e quando. */
    private void stampDecision(PaymentPlaceEntryEntity entry, UserEntity user) {
        entry.setDecidedAt(LocalDateTime.now());
        if (user != null) {
            entry.setDecidedByUserId(user.getId());
            entry.setDecidedByName(user.getName());
        }
    }

    /**
     * Enriquece a agência do lançamento com o endereço real do cadastro Bacen.
     * Resolve banco (COMPE→CNPJ) + número da agência, consulta a API OLINDA,
     * grava endereço + recalcula a coordenada/distância da agência pelo município real.
     */
    public PaymentPlaceEntryEntity enrichAgencyFromBacen(UUID entryId) {
        return agencyEnricher.enrichSingle(entryId);
    }

    /**
     * Consulta (sob demanda) o CNPJ do sacado no CNPJ Já, persiste o endereço/coordenada
     * precisos em company_details e recalcula as distâncias que envolvem o sacado. Uso
     * pontual pelo analista para o cruzamento street-level quando o sacado não está na base.
     */
    @Transactional
    public PaymentPlaceEntryEntity enrichPayerCnpj(UUID entryId) {
        PaymentPlaceEntryEntity entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new EntityNotFoundException("Lançamento de praça de pagamento não encontrado"));

        String cnpj = normalizeCnpj(entry.getPayerDocument());
        if (cnpj == null) {
            throw new IllegalArgumentException("Sacado sem CNPJ válido (14 dígitos) para consulta");
        }

        CompanyDetail company = clientProfileService.enrichByCnpja(cnpj);

        String address = AddressFormat.format(company.getStreet(), company.getNumber(), company.getDetails(),
                company.getDistrict(), company.getCity(), company.getState(), company.getZip());
        entry.setPayerAddress(clean(address));
        if (company.getLatitude() != null && company.getLongitude() != null) {
            entry.setPayerLatitude(BigDecimal.valueOf(company.getLatitude()));
            entry.setPayerLongitude(BigDecimal.valueOf(company.getLongitude()));
        }

        MunicipalityGeocoder.Coordinates payer = coordsOf(entry.getPayerLatitude(), entry.getPayerLongitude());
        MunicipalityGeocoder.Coordinates agency = coordsOf(entry.getAgencyLatitude(), entry.getAgencyLongitude());
        MunicipalityGeocoder.Coordinates client = coordsOf(entry.getClientLatitude(), entry.getClientLongitude());
        entry.setDistanceAgencyPayerKm(geocoder.distanceKm(agency, payer));
        entry.setDistanceClientPayerKm(geocoder.distanceKm(client, payer));

        scorer.apply(entry);
        return entryRepository.save(entry);
    }

    private MunicipalityGeocoder.Coordinates coordsOf(BigDecimal lat, BigDecimal lng) {
        return (lat == null || lng == null) ? null : new MunicipalityGeocoder.Coordinates(lat, lng);
    }

    /** Gera (sob demanda) a justificativa do Gemini para o lançamento e persiste. */
    @Transactional
    public PaymentPlaceEntryEntity analyzeWithAi(UUID entryId) {
        PaymentPlaceEntryEntity entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new EntityNotFoundException("Lançamento de praça de pagamento não encontrado"));

        var result = geminiAiService.analyzePaymentPlace(buildAiContext(entry));
        if (!result.available()) {
            throw new IllegalStateException(result.error() != null ? result.error() : "IA indisponível");
        }
        try {
            entry.setAiAnalysis(objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar análise da IA", e);
        }
        entry.setAiAnalyzedAt(LocalDateTime.now());
        return entryRepository.save(entry);
    }

    private String buildAiContext(PaymentPlaceEntryEntity e) {
        StringBuilder sb = new StringBuilder();
        sb.append("- Título: ").append(nz(e.getTitleNumber())).append("\n");
        sb.append("- Cedente: ").append(nz(e.getClientName())).append(" (cidade no relatório: ").append(nz(e.getClientCity())).append(")\n");
        sb.append("- Sacado: ").append(nz(e.getPayerName())).append(" (cidade: ").append(nz(e.getPayerCity())).append(")\n");
        sb.append("- Agência/banco: ").append(nz(e.getBankAgency())).append(" - ").append(nz(e.getBankName()))
                .append(" (tipo: ").append(nz(e.getBacenInstitutionType())).append(", categoria: ").append(nz(e.getInstitutionCategory())).append(")\n");
        sb.append("- Cidade da agência: ").append(nz(e.getBacenAgencyCity() != null ? e.getBacenAgencyCity() : e.getAgencyCityPdf())).append("\n");
        sb.append("- Confiabilidade geográfica da praça: ").append(nz(e.getGeographicReliability())).append("\n");
        sb.append("- Distância cliente↔agência: ").append(km(e.getDistanceClientAgencyKm())).append("\n");
        sb.append("- Distância agência↔sacado: ").append(km(e.getDistanceAgencyPayerKm())).append("\n");
        sb.append("- Distância cliente↔sacado: ").append(km(e.getDistanceClientPayerKm())).append("\n");
        sb.append("- Ocorrência: ").append(nz(e.getOccurrence())).append("\n");
        sb.append("- Complemento da ocorrência: ").append(nz(e.getOccurrenceComplement())).append("\n");
        sb.append("- Pré-análise determinística: sugestão=").append(nz(e.getAutomaticSuggestion()))
                .append(", confiança=").append(nz(e.getAutomaticConfidence()))
                .append(", score sacado=").append(e.getScoreSacado()).append(", score cedente=").append(e.getScoreCedente()).append("\n");
        sb.append("- Fatores apurados:\n").append(nz(e.getAutomaticEvidence()));
        return sb.toString();
    }

    private String nz(String v) {
        return (v == null || v.isBlank()) ? "—" : v;
    }

    private String km(java.math.BigDecimal v) {
        return v == null ? "indisponível" : v + " km";
    }

    /** Decide vários lançamentos de uma vez (aceitar sugestões em massa). */
    @Transactional
    public List<PaymentPlaceEntryEntity> bulkDecide(java.util.Map<UUID, String> decisionsByEntryId, UserEntity user) {
        if (decisionsByEntryId == null || decisionsByEntryId.isEmpty()) {
            return List.of();
        }
        List<PaymentPlaceEntryEntity> entries = entryRepository.findAllById(decisionsByEntryId.keySet());
        for (PaymentPlaceEntryEntity entry : entries) {
            String normalizedDecision = normalizeDecision(decisionsByEntryId.get(entry.getId()));
            entry.setAnalystDecision(normalizedDecision);
            entry.setAnalysisStatus(ENTRY_STATUS_REVIEWED);
            stampDecision(entry, user);
            if ("CEDENTE".equals(normalizedDecision)) {
                scheduleSacadoProfile(entry.getPayerDocument());
            }
        }
        return entryRepository.saveAll(entries);
    }

    /**
     * Resumo da Praça de Pagamento da empresa. Visão Cedente acumulada = todos os títulos
     * decididos como CEDENTE em que a empresa é o cedente OU o sacado do título. Decisões
     * SACADO não entram (não somam para nenhuma das partes). Totais são sobre TODO o
     * histórico; a lista de lançamentos é filtrada por data e paginada. A Visão Sacado é
     * mantida zerada no retorno por compatibilidade do contrato (o frontend não a exibe mais).
     */
    @Transactional(readOnly = true)
    public CompanyPaymentPlaceSummary getCompanySummary(String cnpj, java.time.LocalDate from, java.time.LocalDate to,
                                                        String decision, int page, int size) {
        String doc = normalizeCnpj(cnpj);
        if (doc == null) {
            return new CompanyPaymentPlaceSummary(cnpj, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO, List.of(), page, size, 0, 0);
        }
        // Regra Visão Cedente: soma os títulos decididos como CEDENTE em que a empresa participa
        // como cedente (clientDocument) OU como sacado (payerDocument). Decisões SACADO não somam
        // para ninguém. A query normaliza ambos os documentos (payer costuma vir mascarado do PDF).
        List<PaymentPlaceEntryEntity> cedenteEntries = entryRepository.findCedenteDecidedForCompany(doc);

        int sacadoCount = 0;
        int cedenteCount = cedenteEntries.size();
        BigDecimal sacadoValue = BigDecimal.ZERO;
        BigDecimal cedenteValue = BigDecimal.ZERO;
        for (PaymentPlaceEntryEntity e : cedenteEntries) {
            cedenteValue = cedenteValue.add(parseBrlValue(e.getPaidValue()));
        }

        java.time.LocalDateTime fromDt = from == null ? null : from.atStartOfDay();
        java.time.LocalDateTime toDt = to == null ? null : to.atTime(23, 59, 59);
        List<PaymentPlaceEntryEntity> filtered = cedenteEntries.stream()
                .filter(e -> fromDt == null || (e.getDecidedAt() != null && !e.getDecidedAt().isBefore(fromDt)))
                .filter(e -> toDt == null || (e.getDecidedAt() != null && !e.getDecidedAt().isAfter(toDt)))
                .toList();

        int safeSize = Math.max(1, Math.min(size, 30));
        int totalFiltered = filtered.size();
        int totalPages = (int) Math.ceil(totalFiltered / (double) safeSize);
        int safePage = Math.max(0, Math.min(page, totalPages == 0 ? 0 : totalPages - 1));
        int fromIdx = safePage * safeSize;
        int toIdx = Math.min(fromIdx + safeSize, totalFiltered);
        List<PaymentPlaceEntryEntity> pageItems = fromIdx >= totalFiltered ? List.of() : filtered.subList(fromIdx, toIdx);

        return new CompanyPaymentPlaceSummary(doc, sacadoCount, sacadoValue, cedenteCount, cedenteValue,
                pageItems, safePage, safeSize, totalPages, totalFiltered);
    }

    /** Converte valor no formato brasileiro ("1.016,45") para BigDecimal; nulo/ inválido → zero. */
    private BigDecimal parseBrlValue(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.replaceAll("[^0-9,]", "").replace(",", "."));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private boolean hasResolvedAgencyLocation(PaymentPlaceEntryEntity entry) {
        return hasText(entry.getAgencyAddressResolved())
                || hasText(entry.getBacenAgencyAddress())
                || hasText(entry.getBacenAgencyCity())
                || hasText(entry.getBacenAgencyName());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String mapSuggestionToDecision(String suggestion) {
        if ("PROVAVEL_SACADO".equals(suggestion)) {
            return "SACADO";
        }
        if ("PROVAVEL_CEDENTE".equals(suggestion)) {
            return "CEDENTE";
        }
        return null;
    }

    private BankAgencyKey toBankAgencyKey(PaymentPlaceEntryEntity entry) {
        return new BankAgencyKey(
                defaultIfBlank(entry.getBankAgency(), "Não informado"),
                defaultIfBlank(entry.getBankName(), defaultIfBlank(entry.getBacenInstitutionName(), "Instituição não identificada")),
                defaultIfBlank(entry.getBankCode(), "—"),
                defaultIfBlank(entry.getAgencyCode(), "—")
        );
    }

    private BankAgencyIndicator toBankAgencyIndicator(BankAgencyKey key, List<PaymentPlaceEntryEntity> entries) {
        List<PaymentPlaceEntryEntity> comparable = entries.stream()
                .filter(entry -> entry.getAnalystDecision() != null)
                .filter(entry -> mapSuggestionToDecision(entry.getAutomaticSuggestion()) != null)
                .toList();
        int agreementCount = (int) comparable.stream()
                .filter(entry -> entry.getAnalystDecision().equals(mapSuggestionToDecision(entry.getAutomaticSuggestion())))
                .count();
        int disagreementCount = comparable.size() - agreementCount;

        return new BankAgencyIndicator(
                key.bankAgency(),
                key.bankName(),
                key.bankCode(),
                key.agencyCode(),
                entries.size(),
                (int) entries.stream().filter(entry -> entry.getAnalystDecision() != null).count(),
                agreementCount,
                disagreementCount,
                percentage(disagreementCount, comparable.size())
        );
    }

    private BigDecimal percentage(int numerator, int denominator) {
        if (denominator <= 0 || numerator <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record CompanyPaymentPlaceSummary(String documentNumber, int sacadoCount, BigDecimal sacadoValue,
                                             int cedenteCount, BigDecimal cedenteValue,
                                             List<PaymentPlaceEntryEntity> entries,
                                             int page, int size, int totalPages, int totalFilteredElements) {
    }

    public record PaymentPlaceBatchIndicators(PaymentPlaceBatchEntity batch,
                                              int totalEntries,
                                              int locatedAgencyCount,
                                              BigDecimal locatedAgencyPct,
                                              int lowReliabilityCount,
                                              BigDecimal lowReliabilityPct,
                                              int comparableDecisionCount,
                                              int agreementCount,
                                              BigDecimal agreementPct,
                                              int disagreementCount,
                                              BigDecimal disagreementPct,
                                              List<BankAgencyIndicator> topRecurringBankAgencies,
                                              List<BankAgencyIndicator> topDivergentBankAgencies) {
    }

    public record BankAgencyIndicator(String bankAgency,
                                      String bankName,
                                      String bankCode,
                                      String agencyCode,
                                      int totalEntries,
                                      int decidedEntries,
                                      int agreementCount,
                                      int disagreementCount,
                                      BigDecimal disagreementPct) {
    }

    private record BankAgencyKey(String bankAgency, String bankName, String bankCode, String agencyCode) {
    }

    @Transactional
    public PaymentPlaceBatchEntity setBatchArchived(UUID batchId, boolean archived) {
        PaymentPlaceBatchEntity batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new EntityNotFoundException("Lote de praça de pagamento não encontrado"));
        batch.setStatus(archived ? STATUS_ARCHIVED : STATUS_IMPORTED);
        return batchRepository.save(batch);
    }

    @Transactional
    public void deleteBatch(UUID batchId) {
        if (!batchRepository.existsById(batchId)) {
            throw new EntityNotFoundException("Lote de praça de pagamento não encontrado");
        }
        batchRepository.deleteById(batchId);
    }

    private PaymentPlaceEntryEntity toEntity(UUID batchId, PaymentPlacePdfParser.PaymentPlaceParsedEntry entry) {
        PaymentPlaceInstitutionClassifier.PaymentPlacePreAnalysis preAnalysis = institutionClassifier.classify(entry);

        MunicipalityGeocoder.Coordinates clientCentroid = geocoder.resolve(entry.getClientCity()).orElse(null);
        MunicipalityGeocoder.Coordinates agency = geocoder.resolve(entry.getAgencyCityPdf()).orElse(null);
        MunicipalityGeocoder.Coordinates payerCentroid = geocoder.resolve(entry.getPayerCity()).orElse(null);

        // Sacado e cedente: se o CNPJ existir em company_details, usa endereço e coordenada
        // precisos (street-level do CNPJ Já); senão, centroide do município (que pode falhar
        // em nomes com apóstrofo, ex. "Santa Bárbara d'Oeste").
        ResolvedAddress sacado = resolveSacado(entry.getPayerDocument(), payerCentroid);
        MunicipalityGeocoder.Coordinates payer = sacado.coordinates();
        ResolvedClient cedente = resolveCedente(entry.getClientCode(), clientCentroid);
        MunicipalityGeocoder.Coordinates client = cedente.coordinates();

        PaymentPlaceEntryEntity built = PaymentPlaceEntryEntity.builder()
                .batchId(batchId)
                .section(entry.getSection())
                .externalId(entry.getExternalId())
                .clientCode(entry.getClientCode())
                .titleNumber(entry.getTitleNumber())
                .dueDate(entry.getDueDate())
                .titleValue(entry.getTitleValue())
                .paidValue(entry.getPaidValue())
                .occurrence(entry.getOccurrence())
                .payerDocument(entry.getPayerDocument())
                .payerName(entry.getPayerName())
                .clientCity(entry.getClientCity())
                .agencyCityPdf(entry.getAgencyCityPdf())
                .payerCity(entry.getPayerCity())
                .bankAgency(entry.getBankAgency())
                .bankCode(entry.getBankCode())
                .agencyCode(entry.getAgencyCode())
                .occurrenceComplement(entry.getOccurrenceComplement())
                .bankName(preAnalysis.getBankName())
                .bacenInstitutionName(preAnalysis.getBacenInstitutionName())
                .bacenInstitutionType(preAnalysis.getBacenInstitutionType())
                .institutionCategory(preAnalysis.getInstitutionCategory())
                .geographicReliability(preAnalysis.getGeographicReliability())
                .geographicReliabilityReason(preAnalysis.getGeographicReliabilityReason())
                .automaticSuggestion(preAnalysis.getAutomaticSuggestion())
                .automaticConfidence(preAnalysis.getAutomaticConfidence())
                .automaticEvidence(preAnalysis.getAutomaticEvidence())
                .clientLatitude(client != null ? client.latitude() : null)
                .clientLongitude(client != null ? client.longitude() : null)
                .agencyLatitude(agency != null ? agency.latitude() : null)
                .agencyLongitude(agency != null ? agency.longitude() : null)
                .payerLatitude(payer != null ? payer.latitude() : null)
                .payerLongitude(payer != null ? payer.longitude() : null)
                .distanceClientAgencyKm(geocoder.distanceKm(client, agency))
                .distanceAgencyPayerKm(geocoder.distanceKm(agency, payer))
                .distanceClientPayerKm(geocoder.distanceKm(client, payer))
                .clientAddress(cedente.address())
                .clientName(cedente.name())
                .clientDocument(cedente.document())
                .payerAddress(sacado.address())
                .analysisStatus(ENTRY_STATUS_PENDING)
                .build();

        // Score determinístico explicável (usa as distâncias já calculadas).
        scorer.apply(built);
        return built;
    }

    /** Endereço + coordenada resolvidos (endereço pode ser nulo; coordenada cai no centroide). */
    private record ResolvedAddress(String address, MunicipalityGeocoder.Coordinates coordinates) {
    }

    /**
     * Resolve o sacado pelo CNPJ no cadastro de empresas (company_details).
     * Usa endereço completo e lat/lng precisos quando disponíveis; caso contrário
     * mantém o centroide do município informado no relatório.
     */
    private ResolvedAddress resolveSacado(String payerDocument, MunicipalityGeocoder.Coordinates fallback) {
        String cnpj = normalizeCnpj(payerDocument);
        if (cnpj == null) {
            return new ResolvedAddress(null, fallback);
        }
        Optional<CompanyDetail> found = companyDetailRepository.findByDocumentNumber(cnpj);
        if (found.isEmpty()) {
            return new ResolvedAddress(null, fallback);
        }
        CompanyDetail company = found.get();
        String address = AddressFormat.format(company.getStreet(), company.getNumber(), company.getDetails(),
                company.getDistrict(), company.getCity(), company.getState(), company.getZip());
        MunicipalityGeocoder.Coordinates coords = fallback;
        if (company.getLatitude() != null && company.getLongitude() != null) {
            coords = new MunicipalityGeocoder.Coordinates(
                    java.math.BigDecimal.valueOf(company.getLatitude()),
                    java.math.BigDecimal.valueOf(company.getLongitude()));
        }
        return new ResolvedAddress(address, coords);
    }

    private String normalizeCnpj(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() != 14) {
            return null;
        }
        return digits;
    }

    /**
     * Monta o endereço do cedente: localiza o cliente pelo código (ERP), pega o CNPJ
     * e prioriza o endereço enriquecido em company_details; cai para o endereço do
     * próprio cadastro de clientes (clientes.csv) quando não houver.
     */
    private record ResolvedClient(String name, String document, String address, MunicipalityGeocoder.Coordinates coordinates) {
    }

    private ResolvedClient resolveCedente(String clientCode, MunicipalityGeocoder.Coordinates fallback) {
        String code = normalizeClientCode(clientCode);
        if (code == null) {
            return new ResolvedClient(null, null, null, fallback);
        }
        return clientRepository.findByClientCode(code).map(client -> {
            var company = companyDetailRepository.findByDocumentNumber(client.getDocumentNumber()).orElse(null);
            String name = company != null && company.getCompanyName() != null ? company.getCompanyName() : client.getName();
            String address = null;
            MunicipalityGeocoder.Coordinates coords = fallback;
            if (company != null) {
                address = AddressFormat.format(company.getStreet(), company.getNumber(), company.getDetails(),
                        company.getDistrict(), company.getCity(), company.getState(), company.getZip());
                if (company.getLatitude() != null && company.getLongitude() != null) {
                    coords = new MunicipalityGeocoder.Coordinates(
                            java.math.BigDecimal.valueOf(company.getLatitude()),
                            java.math.BigDecimal.valueOf(company.getLongitude()));
                }
            }
            if (address == null || address.isBlank()) {
                address = AddressFormat.format(client.getAddressStreet(), client.getAddressNumber(), client.getAddressComplement(),
                        client.getAddressDistrict(), client.getAddressCity(), client.getAddressUf(), client.getAddressZip());
            }
            return new ResolvedClient(name, client.getDocumentNumber(), address, coords);
        }).orElse(new ResolvedClient(null, null, null, fallback));
    }

    /** Código canônico do cliente: dígitos sem zeros à esquerda (PDF "000693" e CSV "693" batem). */
    static String normalizeClientCode(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "").replaceFirst("^0+", "");
        return digits.isEmpty() ? null : digits;
    }

    private String normalizeDecision(String decision) {
        if (decision == null) {
            throw new IllegalArgumentException("Decisão é obrigatória");
        }
        String normalized = decision.trim().toUpperCase();
        if (!normalized.equals("SACADO") && !normalized.equals("CEDENTE")) {
            throw new IllegalArgumentException("Decisão deve ser SACADO ou CEDENTE");
        }
        return normalized;
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class PaymentPlaceImportResult {
        private PaymentPlaceBatchEntity batch;
        private List<PaymentPlaceEntryEntity> entries;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class PaymentPlaceBatchWithEntries {
        private PaymentPlaceBatchEntity batch;
        private List<PaymentPlaceEntryEntity> entries;
    }
}
