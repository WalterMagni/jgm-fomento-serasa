package com.portal.serasa.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extrai o quadro societário do bloco {@code QSAReport} do relatório de empresa da Serasa
 * (coluna {@code credit_analysis.partner_details}).
 *
 * <p>O valor desse bloco para o cruzamento societário é o <b>CPF completo</b>: a base gratuita
 * da Receita só publica a máscara, e nenhum serviço de consulta processual aceita máscara.
 * Ver {@link CpfMask}.</p>
 *
 * <p>Sócios e diretores vêm em listas separadas e a mesma pessoa costuma aparecer nas duas.
 * A deduplicação é por CPF, mantendo a primeira ocorrência e completando os campos que
 * faltarem — {@code partnersList} traz o percentual de capital, {@code directorsList} traz
 * o cargo.</p>
 */
@Slf4j
@Component
public class SerasaQsaExtractor {

    public List<QsaPartner> extract(JsonNode partnerDetails) {
        if (partnerDetails == null || partnerDetails.isMissingNode() || partnerDetails.isNull()) {
            return List.of();
        }
        Map<String, QsaPartner> byCpf = new LinkedHashMap<>();
        collect(partnerDetails.path("partnerCompleteReport").path("partnersList"), byCpf, false);
        collect(partnerDetails.path("directorCompleteReport").path("directorsList"), byCpf, true);
        return new ArrayList<>(byCpf.values());
    }

    private void collect(JsonNode list, Map<String, QsaPartner> byCpf, boolean asDirector) {
        if (!list.isArray()) {
            return;
        }
        for (JsonNode node : list) {
            String cpf = CpfMask.digitsOnly(node.path("documentId").asText(null));
            String name = text(node, "name");
            // Sócio pessoa jurídica também aparece aqui; fica de fora porque a identidade dele
            // na Receita é o CNPJ completo, tratado pela carga da própria base.
            if (cpf == null || cpf.length() != 11 || name == null) {
                continue;
            }
            QsaPartner existing = byCpf.get(cpf);
            if (existing == null) {
                byCpf.put(cpf, new QsaPartner(
                        cpf,
                        name,
                        asDirector ? text(node, "role") : null,
                        text(node, "status"),
                        asDirector ? null : decimal(node, "capitalTotalValue"),
                        date(node, "sinceDate")));
                continue;
            }
            // Completa o que faltar sem sobrescrever o que já veio da outra lista.
            byCpf.put(cpf, new QsaPartner(
                    existing.cpf(),
                    existing.name(),
                    existing.role() != null ? existing.role() : (asDirector ? text(node, "role") : null),
                    existing.status() != null ? existing.status() : text(node, "status"),
                    existing.capitalPercent() != null ? existing.capitalPercent()
                            : (asDirector ? null : decimal(node, "capitalTotalValue")),
                    existing.sinceDate() != null ? existing.sinceDate() : date(node, "sinceDate")));
        }
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.decimalValue() : null;
    }

    private static LocalDate date(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            log.debug("Data do QSA em formato inesperado: {}", value);
            return null;
        }
    }

    /** Sócio com CPF completo, como a Serasa entrega. */
    public record QsaPartner(
            String cpf,
            String name,
            String role,
            String status,
            BigDecimal capitalPercent,
            LocalDate sinceDate) {
    }
}
