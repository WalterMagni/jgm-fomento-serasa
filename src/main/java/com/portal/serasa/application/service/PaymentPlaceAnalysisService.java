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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    @Transactional
    public PaymentPlaceEntryEntity decideEntry(UUID entryId, String decision, String notes, UserEntity user) {
        PaymentPlaceEntryEntity entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new EntityNotFoundException("Lançamento de praça de pagamento não encontrado"));

        String normalizedDecision = normalizeDecision(decision);
        entry.setAnalystDecision(normalizedDecision);
        entry.setAnalystNotes(clean(notes));
        entry.setAnalysisStatus(ENTRY_STATUS_REVIEWED);
        stampDecision(entry, user);
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
            entry.setAnalystDecision(normalizeDecision(decisionsByEntryId.get(entry.getId())));
            entry.setAnalysisStatus(ENTRY_STATUS_REVIEWED);
            stampDecision(entry, user);
        }
        return entryRepository.saveAll(entries);
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
