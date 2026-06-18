package com.portal.serasa.infrastructure.integration.serasa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.serasa.domain.model.CreditAnalysis;
import com.portal.serasa.domain.model.CreditAnalysisStatus;
import com.portal.serasa.infrastructure.integration.serasa.dto.SerasaCreditRatingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SerasaCreditRatingMapper {
    private static final ZoneId BRAZIL_TIMEZONE = ZoneId.of("America/Sao_Paulo");

    private final ObjectMapper objectMapper;

    /**
     * Converte o JSON bruto da API Serasa (RELATORIO_AVANCADO_PJ_ANALITICO) em CreditAnalysis.
     * <p>
     * Mapeamento de colunas JSONB:
     * - negativeSummary  ← reports[0].negativeData
     * - inquiryHistory   ← reports[0].facts
     * - partnerDetails   ← optionalFeatures.QSAReport
     * - creditRatingDetails ← reports[0].identificationReport
     * - paymentHistory   ← reports[0].advancedCommercialPaymentHistory
     * - companyParticipationsReport ← reports[0].checkFilingsHistorical
     */
    public CreditAnalysis toDomain(UUID clientId, String cnpjSolicitado, String rawJson) {
        try {
            SerasaCreditRatingResponse response = objectMapper.readValue(rawJson, SerasaCreditRatingResponse.class);

            if (response.reports() == null || response.reports().isEmpty()) {
                throw new IllegalArgumentException("Retorno da Serasa não contém relatórios.");
            }

            var report = response.reports().get(0);

            // Extrai nome e CNPJ da empresa a partir de identificationReport
            String companyName = null;
            String cnpjRetornado = null;
            if (report.identificationReport() != null) {
                Object nameObj = report.identificationReport().get("companyName");
                if (nameObj != null) companyName = nameObj.toString();
                Object docObj = report.identificationReport().get("documentNumber");
                if (docObj != null) cnpjRetornado = docObj.toString().replaceAll("\\D", "");
            }

            if (cnpjRetornado != null) {
                String cnpjLimpo = cnpjSolicitado.replaceAll("\\D", "");
                if (!cnpjRetornado.equals(cnpjLimpo)) {
                    log.warn("ATENÇÃO: CNPJ solicitado ({}) difere do retornado ({})", cnpjLimpo, cnpjRetornado);
                }
            }

            // Campos de score/risco não existem no novo relatório
            // negativeSummary ← merge de negativeData + negativeSummary textual
            JsonNode negativeJson = mergeNegativeSummary(report.negativeData(), report.negativeSummary());
            // inquiryHistory ← facts
            JsonNode inquiryJson = toJsonNode(report.facts());
            // partnerDetails ← optionalFeatures.QSAReport
            JsonNode partnerJson = (response.optionalFeatures() != null && response.optionalFeatures().qsaReport() != null)
                    ? objectMapper.valueToTree(response.optionalFeatures().qsaReport())
                    : objectMapper.createObjectNode();
            // creditRatingDetails ← identificationReport
            JsonNode creditRatingJson = toJsonNode(report.identificationReport());
            // paymentHistory ← advancedCommercialPaymentHistory
            JsonNode paymentHistoryJson = toJsonNode(report.advancedCommercialPaymentHistory());
            // companyParticipationsReport ← checkFilingsHistorical
            JsonNode checkFilingsJson = toJsonNode(report.checkFilingsHistorical());

            // Detecta Visão Cedente: empresa que opera como cedente em operações de factoring/fomento.
            // Critério: segmentData.assignor possui dados em businessReferencesList OU
            // evolutionCommitmentsSuppliersList OU monthDetail.months com dados não-zero.
            String visaoCedente = detectarVisaoCedente(paymentHistoryJson);
            log.info("CNPJ {} → visaoCedente={}", cnpjSolicitado, visaoCedente);

            return CreditAnalysis.builder()
                    .clientId(clientId)
                    .cnpj(normalizeCnpj(cnpjSolicitado))
                    .companyName(companyName)
                    .score(null)
                    .riskClass(null)
                    .probability(null)
                    .analysisDate(null)
                    .inquiryHistory(inquiryJson)
                    .negativeSummary(negativeJson)
                    .partnerDetails(partnerJson)
                    .creditRatingDetails(creditRatingJson)
                    .paymentHistory(paymentHistoryJson)
                    .companyParticipationsReport(checkFilingsJson)
                    .originalPayload(rawJson)
                    .visaoCedente(visaoCedente)
                    .consultaEm(LocalDateTime.now(BRAZIL_TIMEZONE))
                    .status(CreditAnalysisStatus.CONCLUIDO)
                    .build();

        } catch (JsonProcessingException e) {
            log.error("Erro ao processar JSON da Serasa para CNPJ {}", cnpjSolicitado, e);
            throw new RuntimeException("Falha ao processar resposta da Serasa", e);
        }
    }

    /**
     * Detecta se a empresa possui perfil de cedente inspecionando o nó
     * {@code segmentData.assignor} dentro do JSON de paymentHistory.
     * <p>
     * Retorna "SIM" se houver ao menos um registro em:
     * <ul>
     *   <li>assignor.businessReferences.businessReferencesList</li>
     *   <li>assignor.evolutionCommitmentsSuppliers.evolutionCommitmentsSuppliersList</li>
     *   <li>assignor.paymentHistory.monthDetail.months</li>
     * </ul>
     * Retorna "NAO" se o nó existir mas estiver vazio, ou "PENDENTE" se ausente.
     */
    private String detectarVisaoCedente(JsonNode paymentHistoryJson) {
        if (paymentHistoryJson == null || paymentHistoryJson.isNull() || paymentHistoryJson.isEmpty()) {
            return "PENDENTE";
        }

        JsonNode segmentData = paymentHistoryJson.path("segmentData");
        if (segmentData.isMissingNode()) {
            return "PENDENTE";
        }

        JsonNode assignor = segmentData.path("assignor");
        if (assignor.isMissingNode()) {
            return "NAO";
        }

        // businessReferencesList
        JsonNode brl = assignor.path("businessReferences").path("businessReferencesList");
        if (brl.isArray() && brl.size() > 0) {
            return "SIM";
        }

        // evolutionCommitmentsSuppliersList
        JsonNode ecsl = assignor.path("evolutionCommitmentsSuppliers").path("evolutionCommitmentsSuppliersList");
        if (ecsl.isArray() && ecsl.size() > 0) {
            return "SIM";
        }

        // monthDetail.months
        JsonNode months = assignor.path("paymentHistory").path("monthDetail").path("months");
        if (months.isArray() && months.size() > 0) {
            return "SIM";
        }

        return "NAO";
    }

    private JsonNode toJsonNode(Object obj) {
        if (obj == null) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.valueToTree(obj);
    }

    private JsonNode mergeNegativeSummary(Object negativeData, Object negativeSummary) {
        ObjectNode merged = objectMapper.createObjectNode();

        JsonNode negativeDataNode = toJsonNode(negativeData);
        if (negativeDataNode.isObject()) {
            merged.setAll((ObjectNode) negativeDataNode);
        }

        JsonNode negativeSummaryNode = toJsonNode(negativeSummary);
        if (negativeSummaryNode.isObject()) {
            merged.setAll((ObjectNode) negativeSummaryNode);
        }

        return merged;
    }

    private String normalizeCnpj(String cnpj) {
        if (cnpj == null || cnpj.isBlank()) return null;
        String digits = cnpj.replaceAll("\\D", "");
        return digits.length() >= 14 ? digits.substring(0, 14) : digits;
    }
}
