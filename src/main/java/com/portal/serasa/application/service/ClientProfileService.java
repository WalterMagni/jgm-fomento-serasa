package com.portal.serasa.application.service;

import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.domain.model.Client;
import com.portal.serasa.domain.model.CompanyDetail;
import com.portal.serasa.domain.model.CreditAnalysis;
import com.portal.serasa.domain.model.PersonAnalysis;
import com.portal.serasa.application.port.out.ClientRepository;
import com.portal.serasa.application.port.out.CreditAnalysisRepository;
import com.portal.serasa.application.port.out.PersonAnalysisRepository;
import com.portal.serasa.infrastructure.email.EmailService;
import com.portal.serasa.infrastructure.integration.serasa.SerasaCreditRatingClient;
import com.portal.serasa.infrastructure.integration.serasa.SerasaCreditRatingMapper;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Serviço que orquestra dados do cliente vindos de CNPJ Já e Serasa.
 * Mapa de informações: quando buscamos por CNPJ, trazemos dados de ambas as
 * APIs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClientProfileService {

    private final ClientRepository clientRepository;
    private final CompanyDetailService companyDetailService;
    private final CreditAnalysisRepository creditAnalysisRepository;
    private final PersonAnalysisRepository personAnalysisRepository;
    private final SerasaCreditRatingClient serasaCreditRatingClient;
    private final SerasaCreditRatingMapper serasaCreditRatingMapper;
    private final ApiUsageLogService apiUsageLogService;
    private final EmailService emailService;

    @Value("${billing.serasa.cost-per-query:0.00}")
    private BigDecimal serasaCostPerQuery;

    /**
     * Enriquece dados da empresa consultando a API Serasa (Credit Rating).
     * Requer que o cliente já esteja cadastrado (CNPJ deve existir em clients).
     */
    public CreditAnalysis enrichBySerasa(String cnpj) {
        String documentNumber = normalizeCnpj(cnpj);
        if (documentNumber == null || documentNumber.length() != 14) {
            throw new IllegalArgumentException("CNPJ deve conter 14 dígitos numéricos");
        }

        CompanyDetail companyDetail = resolveCompanyDetailForSerasa(documentNumber);
        Client client = ensureClient(
                documentNumber,
                companyDetail != null ? companyDetail.getCompanyName() : null);

        log.info("Enriquecendo dados Serasa para cliente CNPJ: {}", documentNumber);

        boolean isInitial = creditAnalysisRepository.findLatestByCnpj(documentNumber).isEmpty();
        String queryType = isInitial ? "INITIAL" : "UPDATE";

        String rawJson = serasaCreditRatingClient.consultarCreditRating(documentNumber);
        CreditAnalysis saved = saveSerasaAnalysis(client, documentNumber, rawJson);

        String currentUserName = resolveCurrentUserName();
        String companyName = saved.getCompanyName() != null ? saved.getCompanyName() : client.getName();
        apiUsageLogService.save(currentUserName, companyName, documentNumber, queryType, serasaCostPerQuery);

        // Notifica equipe comercial se empresa é Visão Cedente e usuário tem notificação habilitada
        if ("SIM".equals(saved.getVisaoCedente()) && resolveCurrentUserEmailNotificacao()) {
            emailService.notificarCedenteAsync(saved);
        }

        replicateSerasaAnalysisToRegisteredGroup(documentNumber, saved);

        return saved;
    }

    /**
     * Processa um JSON bruto da Serasa (sem chamar a API real) e salva como análise de crédito.
     * Útil para testes de desenvolvimento sem gastar créditos.
     */
    public CreditAnalysis enrichBySerasaFromJson(String cnpj, String rawJson) {
        String documentNumber = normalizeCnpj(cnpj);
        if (documentNumber == null || documentNumber.length() != 14) {
            throw new IllegalArgumentException("CNPJ deve conter 14 dígitos numéricos");
        }

        CompanyDetail companyDetail = resolveCompanyDetailForSerasa(documentNumber);
        Client client = ensureClient(
                documentNumber,
                companyDetail != null ? companyDetail.getCompanyName() : null);

        log.info("Enriquecendo dados Serasa (mock) para cliente CNPJ: {}", documentNumber);

        CreditAnalysis saved = saveSerasaAnalysis(client, documentNumber, rawJson);
        replicateSerasaAnalysisToRegisteredGroup(documentNumber, saved);
        return saved;
    }

    /**
     * Enriquece dados da empresa consultando a API CNPJA e garante
     * que o cliente principal exista na base.
     */
    public CompanyDetail enrichByCnpja(String cnpj) {
        String documentNumber = normalizeCnpj(cnpj);
        if (documentNumber == null || documentNumber.length() != 14) {
            throw new IllegalArgumentException("CNPJ deve conter 14 dígitos numéricos");
        }

        Optional<CompanyDetail> existing = companyDetailService.findByDocumentNumber(documentNumber);
        if (existing.isPresent()) {
            return existing.get();
        }

        CompanyDetail companyDetail = companyDetailService.enrichByCnpj(documentNumber);

        ensureClient(documentNumber, companyDetail.getCompanyName());

        return companyDetail;
    }

    private CreditAnalysis saveSerasaAnalysis(Client client, String documentNumber, String rawJson) {
        CreditAnalysis analysis = serasaCreditRatingMapper.toDomain(client.getId(), documentNumber, rawJson);
        return creditAnalysisRepository.save(analysis);
    }

    private CompanyDetail resolveCompanyDetailForSerasa(String documentNumber) {
        Optional<CompanyDetail> existing = companyDetailService.findByDocumentNumber(documentNumber);
        if (existing.isPresent()) {
            return existing.get();
        }

        String headOfficeDocumentNumber = calculateHeadOfficeCnpj(documentNumber);
        boolean isHeadOffice = documentNumber.equals(headOfficeDocumentNumber);
        if (!isHeadOffice && companyDetailService.findByDocumentNumber(headOfficeDocumentNumber).isEmpty()) {
            throw new EntityNotFoundException(
                    "Cadastre a matriz " + headOfficeDocumentNumber + " antes de criar ou consultar a filial " + documentNumber);
        }

        return enrichByCnpja(documentNumber);
    }

    private void replicateSerasaAnalysisToRegisteredGroup(String sourceDocumentNumber, CreditAnalysis sourceAnalysis) {
        Set<String> targetDocumentNumbers = findRegisteredGroupDocumentNumbers(sourceDocumentNumber);
        for (String targetDocumentNumber : targetDocumentNumbers) {
            if (sourceDocumentNumber.equals(targetDocumentNumber)) {
                continue;
            }

            Optional<CompanyDetail> targetDetail = companyDetailService.findByDocumentNumber(targetDocumentNumber);
            Client targetClient = ensureClient(
                    targetDocumentNumber,
                    targetDetail.map(CompanyDetail::getCompanyName).orElse(null));

            CreditAnalysis replicatedAnalysis = copyAnalysisToDocument(
                    sourceAnalysis,
                    targetClient.getId(),
                    targetDocumentNumber);
            creditAnalysisRepository.save(replicatedAnalysis);
        }

        if (targetDocumentNumbers.size() > 1) {
            log.info("Consulta Serasa de {} replicada para {} CNPJ(s) do mesmo grupo.",
                    sourceDocumentNumber, targetDocumentNumbers.size() - 1);
        }
    }

    private Set<String> findRegisteredGroupDocumentNumbers(String documentNumber) {
        String documentRoot = documentNumber.substring(0, 8);
        Set<String> documentNumbers = new LinkedHashSet<>();
        clientRepository.findByDocumentNumberStartingWith(documentRoot).stream()
                .map(Client::getDocumentNumber)
                .filter(doc -> doc != null && doc.length() == 14)
                .forEach(documentNumbers::add);

        String headOfficeDocumentNumber = calculateHeadOfficeCnpj(documentNumber);
        companyDetailService.findByDocumentNumber(headOfficeDocumentNumber)
                .map(CompanyDetail::getDocumentNumber)
                .ifPresent(documentNumbers::add);
        documentNumbers.add(documentNumber);
        return documentNumbers;
    }

    private Client ensureClient(String documentNumber, String companyName) {
        return clientRepository.findByDocumentNumber(documentNumber)
                .map(client -> {
                    if ((client.getName() == null || client.getName().startsWith("Cliente "))
                            && companyName != null && !companyName.isBlank()) {
                        client.setName(companyName);
                        return clientRepository.save(client);
                    }
                    return client;
                })
                .orElseGet(() -> {
                    log.info("Cliente não encontrado. Criando cliente para o CNPJ: {}", documentNumber);
                    Client newClient = Client.builder()
                            .documentNumber(documentNumber)
                            .name(companyName != null && !companyName.isBlank()
                                    ? companyName
                                    : "Cliente " + documentNumber)
                            .build();
                    return clientRepository.save(newClient);
                });
    }

    private CreditAnalysis copyAnalysisToDocument(CreditAnalysis source, java.util.UUID clientId, String documentNumber) {
        return CreditAnalysis.builder()
                .clientId(clientId)
                .cnpj(documentNumber)
                .companyName(source.getCompanyName())
                .score(source.getScore())
                .riskClass(source.getRiskClass())
                .probability(source.getProbability())
                .analysisDate(source.getAnalysisDate())
                .inquiryHistory(source.getInquiryHistory())
                .negativeSummary(source.getNegativeSummary())
                .partnerDetails(source.getPartnerDetails())
                .creditRatingDetails(source.getCreditRatingDetails())
                .paymentHistory(source.getPaymentHistory())
                .companyParticipationsReport(source.getCompanyParticipationsReport())
                .originalPayload(source.getOriginalPayload())
                .visaoCedente(source.getVisaoCedente())
                .consultaEm(source.getConsultaEm())
                .status(source.getStatus())
                .build();
    }

    private String calculateHeadOfficeCnpj(String cnpj) {
        String documentNumber = normalizeCnpj(cnpj);
        if (documentNumber == null || documentNumber.length() != 14) {
            return null;
        }

        String base = documentNumber.substring(0, 8) + "0001";
        return base + calculateCnpjCheckDigits(base);
    }

    private String calculateCnpjCheckDigits(String firstTwelveDigits) {
        int firstDigit = calculateCnpjCheckDigit(firstTwelveDigits, new int[]{5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        int secondDigit = calculateCnpjCheckDigit(firstTwelveDigits + firstDigit, new int[]{6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        return String.valueOf(firstDigit) + secondDigit;
    }

    private int calculateCnpjCheckDigit(String digits, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += Character.digit(digits.charAt(i), 10) * weights[i];
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    /**
     * Re-sincroniza dados da empresa conforme target: CNPJA (dados cadastrais),
     * SERASA (nova análise de crédito) ou ALL (ambos).
     * Retorna o perfil atualizado.
     */
    public ClientProfile resync(String cnpj, ResyncTarget target) {
        String documentNumber = normalizeCnpj(cnpj);
        if (documentNumber == null || documentNumber.length() != 14) {
            throw new IllegalArgumentException("CNPJ deve conter 14 dígitos numéricos");
        }

        if (target == ResyncTarget.CNPJA || target == ResyncTarget.ALL) {
            enrichByCnpja(documentNumber);
        }
        if (target == ResyncTarget.SERASA || target == ResyncTarget.ALL) {
            enrichBySerasa(documentNumber);
        }

        return getProfileByCnpj(documentNumber);
    }

    /**
     * Retorna uma página de perfis paginada sobre todos os clientes cadastrados.
     * Usa a tabela de clients como base (left-join lógico): clientes sem
     * company_detail
     * ou credit_analysis também aparecem, com esses campos nulos.
     */
    public Page<ClientProfile> findAllProfiles(Pageable pageable, String search) {
        return findAllProfiles(pageable, search, null, null);
    }

    public Page<ClientProfile> findAllProfiles(Pageable pageable, String search, String visaoCedente, String analysisStatus) {
        Pageable sorted = org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));

        Page<Client> clients = clientRepository.searchProfiles(search, visaoCedente, analysisStatus, sorted);

        List<String> documentNumbers = clients.getContent().stream()
                .map(Client::getDocumentNumber)
                .filter(documentNumber -> documentNumber != null && !documentNumber.isBlank())
                .toList();
        Map<String, CompanyDetail> companyDetailsByDocument = companyDetailService
                .findByDocumentNumbers(documentNumbers)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        CompanyDetail::getDocumentNumber,
                        java.util.function.Function.identity(),
                        (first, ignored) -> first));
        Map<String, CreditAnalysis> latestAnalysisByDocument = creditAnalysisRepository
                .findLatestByCnpjIn(documentNumbers)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        CreditAnalysis::getCnpj,
                        java.util.function.Function.identity(),
                        (first, ignored) -> first));

        return clients.map(client -> {
            String documentNumber = client.getDocumentNumber();
            return ClientProfile.builder()
                    .client(client)
                    .companyDetail(companyDetailsByDocument.get(documentNumber))
                    .creditAnalysis(latestAnalysisByDocument.get(documentNumber))
                    .build();
        });
    }

    public PortfolioMetrics getPortfolioMetrics() {
        long totalClients = clientRepository.count();
        long cnpjaEnrichedCount = companyDetailService.countRegisteredEnrichedClients();
        CreditAnalysisRepository.PortfolioAnalysisMetrics analysisMetrics =
                creditAnalysisRepository.getPortfolioAnalysisMetrics();
        return PortfolioMetrics.builder()
                .totalClients(totalClients)
                .analyzedClients(analysisMetrics.analyzedClients())
                .avgScore(analysisMetrics.avgScore())
                .highRiskCount(analysisMetrics.highRiskCount())
                .totalDebt(0)
                .cnpjaEnrichedCount(cnpjaEnrichedCount)
                .cedenteSimCount(analysisMetrics.cedenteSimCount())
                .pendingAnalysisCount(Math.max(totalClients - analysisMetrics.analyzedClients(), 0))
                .build();
    }

    /**
     * Retorna o perfil completo do cliente por CNPJ: dados CNPJ Já + Serasa.
     * Também enriquece os sócios com análises PF já salvas.
     */
    public ClientProfile getProfileByCnpj(String cnpj) {
        return getProfileByCnpj(cnpj, null);
    }

    public ClientProfile getProfileByCnpj(String cnpj, Long analysisId) {
        String documentNumber = normalizeCnpj(cnpj);

        Optional<CompanyDetail> companyDetail = companyDetailService.findByDocumentNumber(documentNumber);
        List<CreditAnalysis> analysisHistory = creditAnalysisRepository.findByCnpj(documentNumber).stream()
                .sorted((a, b) -> {
                    if (a.getConsultaEm() == null && b.getConsultaEm() == null) return 0;
                    if (a.getConsultaEm() == null) return 1;
                    if (b.getConsultaEm() == null) return -1;
                    return b.getConsultaEm().compareTo(a.getConsultaEm());
                })
                .toList();

        Optional<CreditAnalysis> creditAnalysis = analysisId != null
                ? analysisHistory.stream()
                    .filter(item -> analysisId.equals(item.getId()))
                    .findFirst()
                : analysisHistory.stream().findFirst();
        if (creditAnalysis.isEmpty()) {
            creditAnalysis = analysisHistory.stream().findFirst();
        }
        Optional<Client> client = clientRepository.findByDocumentNumber(documentNumber);

        Map<String, PersonAnalysis> partnerPfAnalyses = resolvePartnerPfAnalyses(creditAnalysis.orElse(null));

        return ClientProfile.builder()
                .client(client.orElse(null))
                .companyDetail(companyDetail.orElse(null))
                .creditAnalysis(creditAnalysis.orElse(null))
                .analysisHistory(analysisHistory)
                .partnerPfAnalyses(partnerPfAnalyses)
                .build();
    }

    /**
     * Extrai os CPFs dos sócios do relatório QSA e retorna as análises PF disponíveis.
     */
    private Map<String, PersonAnalysis> resolvePartnerPfAnalyses(CreditAnalysis creditAnalysis) {
        if (creditAnalysis == null || creditAnalysis.getPartnerDetails() == null) {
            return Map.of();
        }
        try {
            JsonNode partners = creditAnalysis.getPartnerDetails()
                    .path("partnerCompleteReport")
                    .path("partnersList");
            if (!partners.isArray() || partners.isEmpty()) return Map.of();

            List<String> cpfs = new ArrayList<>();
            for (JsonNode partner : partners) {
                String docId = partner.path("documentId").asText(null);
                if (docId != null) {
                    String digits = docId.replaceAll("\\D", "");
                    if (digits.length() == 11) {
                        cpfs.add(digits);
                    }
                }
            }
            if (cpfs.isEmpty()) return Map.of();

            return personAnalysisRepository.findLatestByCpfIn(cpfs);
        } catch (Exception e) {
            log.warn("Erro ao resolver análises PF dos sócios para CNPJ={}", creditAnalysis.getCnpj(), e);
            return Map.of();
        }
    }

    private String resolveCurrentUserName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserEntity user) {
            return user.getName();
        }
        return "Sistema";
    }

    private boolean resolveCurrentUserEmailNotificacao() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserEntity user) {
            return user.isEmailNotificacaoCedente();
        }
        return true; // default: envia se usuário não identificado
    }

    private String normalizeCnpj(String cnpj) {
        if (cnpj == null || cnpj.isBlank())
            return null;
        String digits = cnpj.replaceAll("\\D", "");
        return digits.length() >= 14 ? digits.substring(0, 14) : digits;
    }

    /**
     * Exclui o perfil completo do cliente: Análise de Crédito, Detalhes da Empresa
     * e Cliente.
     */
    @org.springframework.transaction.annotation.Transactional
    public void deleteProfile(String cnpj) {
        String documentNumber = normalizeCnpj(cnpj);
        if (documentNumber == null)
            return;

        creditAnalysisRepository.deleteAllByCnpj(documentNumber);

        try {
            companyDetailService.deleteByDocumentNumber(documentNumber);
        } catch (EntityNotFoundException e) {
            // Pode não existir, ignora
        }

        try {
            clientRepository.deleteByDocumentNumber(documentNumber);
        } catch (Exception e) {
            // Ignora se não existir
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class ClientProfile {
        private Client client;
        private CompanyDetail companyDetail;
        private CreditAnalysis creditAnalysis;
        private List<CreditAnalysis> analysisHistory;
        /** Análises PF dos sócios: key = CPF (11 dígitos) */
        private Map<String, PersonAnalysis> partnerPfAnalyses;
    }

    @lombok.Data
    @lombok.Builder
    public static class PortfolioMetrics {
        private long totalClients;
        private long analyzedClients;
        private long avgScore;
        private long highRiskCount;
        private double totalDebt;
        private long cnpjaEnrichedCount;
        private long cedenteSimCount;
        private long pendingAnalysisCount;
    }

    public enum ResyncTarget {
        CNPJA, SERASA, ALL
    }
}
