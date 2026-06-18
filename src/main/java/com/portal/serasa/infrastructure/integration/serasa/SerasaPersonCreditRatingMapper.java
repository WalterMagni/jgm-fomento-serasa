package com.portal.serasa.infrastructure.integration.serasa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.serasa.domain.model.PersonAnalysis;
import com.portal.serasa.infrastructure.integration.serasa.dto.SerasaPersonCreditRatingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Converte o JSON bruto da API Serasa (RELATORIO_AVANCADO_PF) em PersonAnalysis.
 * <p>
 * Mapeamento de colunas JSONB:
 * - registration       ← reports[0].registration
 * - negativeSummary    ← reports[0].negativeData
 * - facts              ← reports[0].facts
 * - partnerCompanies   ← reports[0].partner
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SerasaPersonCreditRatingMapper {
    private static final ZoneId BRAZIL_TIMEZONE = ZoneId.of("America/Sao_Paulo");

    private final ObjectMapper objectMapper;

    public PersonAnalysis toDomain(String cpfSolicitado, String rawJson) {
        try {
            SerasaPersonCreditRatingResponse response =
                    objectMapper.readValue(rawJson, SerasaPersonCreditRatingResponse.class);

            if (response.reports() == null || response.reports().isEmpty()) {
                throw new IllegalArgumentException("Retorno da Serasa PF não contém relatórios.");
            }

            SerasaPersonCreditRatingResponse.Report report = response.reports().get(0);

            String personName = extractPersonName(report);
            String cpfNormalizado = normalizeCpf(cpfSolicitado);

            JsonNode registrationJson    = toJsonNode(report.registration());
            JsonNode negativeJson        = toJsonNode(report.negativeData());
            JsonNode factsJson           = toJsonNode(report.facts());
            JsonNode partnerCompaniesJson = toJsonNode(report.partner());

            return PersonAnalysis.builder()
                    .cpf(cpfNormalizado)
                    .personName(personName)
                    .registration(registrationJson)
                    .negativeSummary(negativeJson)
                    .facts(factsJson)
                    .partnerCompanies(partnerCompaniesJson)
                    .originalPayload(rawJson)
                    .consultaEm(LocalDateTime.now(BRAZIL_TIMEZONE))
                    .status("CONCLUIDO")
                    .build();

        } catch (JsonProcessingException e) {
            log.error("Erro ao processar JSON da Serasa PF para CPF {}", cpfSolicitado, e);
            throw new RuntimeException("Falha ao processar resposta da Serasa PF", e);
        }
    }

    private String extractPersonName(SerasaPersonCreditRatingResponse.Report report) {
        if (report.registration() == null) return null;
        Object nameObj = report.registration().get("consumerName");
        return nameObj != null ? nameObj.toString() : null;
    }

    private JsonNode toJsonNode(Object obj) {
        if (obj == null) return objectMapper.createObjectNode();
        return objectMapper.valueToTree(obj);
    }

    private String normalizeCpf(String cpf) {
        if (cpf == null || cpf.isBlank()) return null;
        String digits = cpf.replaceAll("\\D", "");
        return digits.length() >= 11 ? digits.substring(0, 11) : digits;
    }
}
