package com.portal.serasa.application.service;

import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class PaymentPlaceInstitutionClassifier {

    private static final String CATEGORY_TRADITIONAL = "TRADICIONAL";
    private static final String CATEGORY_DIGITAL = "DIGITAL";
    private static final String CATEGORY_COOPERATIVE = "COOPERATIVA";
    private static final String CATEGORY_FINANCIAL = "FINANCEIRA";
    private static final String CATEGORY_UNKNOWN = "INDETERMINADA";

    private static final Set<String> UNLOCATED_AGENCY_VALUES = Set.of(
            "AGENCIA NAO LOCALIZADA",
            "AGÊNCIA NÃO LOCALIZADA"
    );

    private static final Map<String, InstitutionProfile> BANK_PROFILES = Map.ofEntries(
            profile("001", "Banco do Brasil", "Banco Múltiplo", CATEGORY_TRADITIONAL),
            profile("021", "Banestes", "Banco Múltiplo", CATEGORY_TRADITIONAL),
            profile("033", "Santander", "Banco Múltiplo", CATEGORY_TRADITIONAL),
            profile("077", "Banco Inter", "Banco Múltiplo", CATEGORY_DIGITAL),
            profile("085", "Cooperativa Central Ailos", "Cooperativa de Crédito", CATEGORY_COOPERATIVE),
            profile("097", "Credisis", "Cooperativa de Crédito", CATEGORY_COOPERATIVE),
            profile("104", "Caixa Econômica Federal", "Caixa Econômica Federal", CATEGORY_TRADITIONAL),
            profile("208", "BTG Pactual", "Banco Múltiplo", CATEGORY_TRADITIONAL),
            profile("212", "Banco Original", "Banco Múltiplo", CATEGORY_DIGITAL),
            profile("237", "Bradesco", "Banco Múltiplo", CATEGORY_TRADITIONAL),
            profile("260", "Nu Pagamentos", "Instituição de Pagamento", CATEGORY_DIGITAL),
            profile("290", "PagBank", "Instituição de Pagamento", CATEGORY_DIGITAL),
            profile("323", "Mercado Pago", "Instituição de Pagamento", CATEGORY_DIGITAL),
            profile("336", "C6 Bank", "Banco Múltiplo", CATEGORY_DIGITAL),
            profile("341", "Itaú Unibanco", "Banco Múltiplo", CATEGORY_TRADITIONAL),
            profile("364", "Efí", "Instituição de Pagamento", CATEGORY_DIGITAL),
            profile("380", "PicPay", "Instituição de Pagamento", CATEGORY_DIGITAL),
            profile("403", "Cora", "Instituição de Pagamento", CATEGORY_DIGITAL),
            profile("422", "Banco Safra", "Banco Múltiplo", CATEGORY_TRADITIONAL),
            profile("461", "Asaas", "Instituição de Pagamento", CATEGORY_DIGITAL),
            profile("633", "Banco Rendimento", "Banco Comercial", CATEGORY_FINANCIAL),
            profile("655", "Banco Votorantim", "Banco Múltiplo", CATEGORY_TRADITIONAL),
            profile("707", "Banco Daycoval", "Banco Múltiplo", CATEGORY_TRADITIONAL),
            profile("735", "Banco Neon", "Banco Múltiplo", CATEGORY_DIGITAL),
            profile("748", "Sicredi", "Cooperativa de Crédito", CATEGORY_COOPERATIVE),
            profile("756", "Sicoob", "Cooperativa de Crédito", CATEGORY_COOPERATIVE)
    );

    public PaymentPlacePreAnalysis classify(PaymentPlacePdfParser.PaymentPlaceParsedEntry entry) {
        InstitutionProfile profile = BANK_PROFILES.getOrDefault(normalizeBankCode(entry.getBankCode()), unknownProfile(entry.getBankCode()));
        boolean agencyUnlocated = isAgencyUnlocated(entry.getAgencyCityPdf());

        String agencyCity = normalizeCity(entry.getAgencyCityPdf());
        String payerCity = normalizeCity(entry.getPayerCity());
        String clientCity = normalizeCity(entry.getClientCity());
        String complement = normalizeText(entry.getOccurrenceComplement());

        DerivedAnalysis derived = deriveAnalysis(profile, agencyUnlocated, agencyCity, payerCity, clientCity, complement);

        return PaymentPlacePreAnalysis.builder()
                .bankName(profile.name())
                .bacenInstitutionName(profile.name())
                .bacenInstitutionType(profile.bacenType())
                .institutionCategory(profile.category())
                .geographicReliability(derived.reliability())
                .geographicReliabilityReason(derived.reliabilityReason())
                .automaticSuggestion(derived.suggestion())
                .automaticConfidence(derived.confidence())
                .automaticEvidence(derived.evidence())
                .build();
    }

    public void applyBacenClassification(com.portal.serasa.infrastructure.persistence.entity.PaymentPlaceEntryEntity entry,
                                         String bacenSegment,
                                         String institutionName) {
        InstitutionProfile profile = profileFromBacenSegment(bacenSegment)
                .orElseGet(() -> BANK_PROFILES.getOrDefault(normalizeBankCode(entry.getBankCode()), unknownProfile(entry.getBankCode())));
        boolean agencyUnlocated = isAgencyUnlocated(entry.getBacenAgencyCity() != null ? entry.getBacenAgencyCity() : entry.getAgencyCityPdf());

        DerivedAnalysis derived = deriveAnalysis(
                profile,
                agencyUnlocated,
                normalizeCity(entry.getBacenAgencyCity() != null ? entry.getBacenAgencyCity() : entry.getAgencyCityPdf()),
                normalizeCity(entry.getPayerCity()),
                normalizeCity(entry.getClientCity()),
                normalizeText(entry.getOccurrenceComplement())
        );

        entry.setBankName(blankToNull(institutionName) != null ? institutionName.trim() : profile.name());
        entry.setBacenInstitutionName(blankToNull(institutionName) != null ? institutionName.trim() : profile.name());
        entry.setBacenInstitutionType(profile.bacenType());
        entry.setInstitutionCategory(profile.category());
        entry.setGeographicReliability(derived.reliability());
        entry.setGeographicReliabilityReason(derived.reliabilityReason());
    }

    private static Map.Entry<String, InstitutionProfile> profile(String code, String name, String bacenType, String category) {
        return Map.entry(code, new InstitutionProfile(name, bacenType, category));
    }

    private static InstitutionProfile unknownProfile(String bankCode) {
        String suffix = bankCode == null || bankCode.isBlank() ? "" : " " + bankCode.trim();
        return new InstitutionProfile("Banco" + suffix + " não classificado", "Não identificado", CATEGORY_UNKNOWN);
    }

    private java.util.Optional<InstitutionProfile> profileFromBacenSegment(String segment) {
        String normalized = normalizeText(segment);
        if (normalized.isBlank()) {
            return java.util.Optional.empty();
        }
        if (normalized.contains("COOPERATIVA")) {
            return java.util.Optional.of(new InstitutionProfile("Instituição Bacen", cleanLabel(segment), CATEGORY_COOPERATIVE));
        }
        if (normalized.contains("INSTITUICAO DE PAGAMENTO") || normalized.contains("IP")) {
            return java.util.Optional.of(new InstitutionProfile("Instituição Bacen", cleanLabel(segment), CATEGORY_DIGITAL));
        }
        if (normalized.contains("SOCIEDADE DE CREDITO, FINANCIAMENTO E INVESTIMENTO")
                || normalized.contains("SCFI")
                || normalized.contains("SOCIEDADE DE CREDITO DIRETO")
                || normalized.contains("SCD")
                || normalized.contains("SOCIEDADE DE EMPRESTIMO")
                || normalized.contains("SEP")
                || normalized.contains("FINANCEIRA")) {
            return java.util.Optional.of(new InstitutionProfile("Instituição Bacen", cleanLabel(segment), CATEGORY_FINANCIAL));
        }
        if (normalized.contains("BANCO")
                || normalized.contains("CAIXA ECONOMICA")
                || normalized.contains("BANCO MULTIPLO")
                || normalized.contains("BANCO COMERCIAL")) {
            return java.util.Optional.of(new InstitutionProfile("Instituição Bacen", cleanLabel(segment), CATEGORY_TRADITIONAL));
        }
        return java.util.Optional.of(new InstitutionProfile("Instituição Bacen", cleanLabel(segment), CATEGORY_UNKNOWN));
    }

    private DerivedAnalysis deriveAnalysis(InstitutionProfile profile,
                                           boolean agencyUnlocated,
                                           String agencyCity,
                                           String payerCity,
                                           String clientCity,
                                           String complement) {
        String reliability = resolveReliability(profile, agencyUnlocated);
        String reliabilityReason = resolveReliabilityReason(profile, agencyUnlocated);

        java.util.List<String> evidence = new java.util.ArrayList<>();
        evidence.add("Instituição classificada como " + labelCategory(profile.category()) + ".");
        evidence.add(reliabilityReason);

        String suggestion = "INCONCLUSIVO";
        String confidence = "BAIXA";

        if (agencyUnlocated) {
            evidence.add("A agência veio como não localizada no relatório.");
        } else if (!agencyCity.isBlank() && agencyCity.equals(payerCity)) {
            suggestion = "PROVAVEL_SACADO";
            confidence = confidenceForReliability(reliability);
            evidence.add("Município da agência coincide com o município do sacado.");
        } else if (!agencyCity.isBlank() && agencyCity.equals(clientCity)) {
            suggestion = "PROVAVEL_CEDENTE";
            confidence = confidenceForReliability(reliability);
            evidence.add("Município da agência coincide com o município do cliente/cedente.");
        }

        if (complement.contains("PRAÇA DO CLIENTE") || complement.contains("PRACA DO CLIENTE")
                || complement.contains("AGENCIA DO CLIENTE") || complement.contains("AGÊNCIA DO CLIENTE")) {
            evidence.add("Complemento da ocorrência indica vínculo com a praça/agência do cliente.");
            if ("INCONCLUSIVO".equals(suggestion) && !agencyUnlocated) {
                suggestion = "PROVAVEL_CEDENTE";
                confidence = "MEDIA";
            }
        }

        if (complement.contains("FORA DA PRAÇA DO SACADO") || complement.contains("FORA DA PRACA DO SACADO")) {
            evidence.add("Complemento indica pagamento fora da praça do sacado.");
            if ("PROVAVEL_SACADO".equals(suggestion) && !"ALTA".equals(confidence)) {
                confidence = "MEDIA";
            }
        }

        if ("BAIXA".equals(reliability) || "INDETERMINADA".equals(reliability)) {
            confidence = "INCONCLUSIVO".equals(suggestion) ? "BAIXA" : "MEDIA";
            evidence.add("A confiabilidade geográfica reduz o peso da praça na decisão.");
        }
        return new DerivedAnalysis(reliability, reliabilityReason, suggestion, confidence, String.join("\n", evidence));
    }

    private static String resolveReliability(InstitutionProfile profile, boolean agencyUnlocated) {
        if (agencyUnlocated) {
            return "BAIXA";
        }
        return switch (profile.category()) {
            case CATEGORY_TRADITIONAL -> "ALTA";
            case CATEGORY_COOPERATIVE, CATEGORY_FINANCIAL -> "MEDIA";
            case CATEGORY_DIGITAL -> "BAIXA";
            default -> "INDETERMINADA";
        };
    }

    private static String resolveReliabilityReason(InstitutionProfile profile, boolean agencyUnlocated) {
        if (agencyUnlocated) {
            return "Agência não localizada no relatório; a praça bancária tem baixo valor geográfico.";
        }
        return switch (profile.category()) {
            case CATEGORY_TRADITIONAL -> "Banco tradicional com maior chance de agência física representar a praça de pagamento.";
            case CATEGORY_COOPERATIVE -> "Cooperativa pode ter vínculo regional, mas a agência nem sempre representa o local real do pagamento.";
            case CATEGORY_DIGITAL -> "Instituição digital ou de pagamento; agência/código operacional tem baixo valor geográfico.";
            case CATEGORY_FINANCIAL -> "Instituição financeira com confiabilidade geográfica intermediária para análise de praça.";
            default -> "Instituição ainda não classificada; a praça deve ser revisada manualmente.";
        };
    }

    private static String confidenceForReliability(String reliability) {
        return switch (reliability) {
            case "ALTA" -> "ALTA";
            case "MEDIA" -> "MEDIA";
            default -> "BAIXA";
        };
    }

    private static String labelCategory(String category) {
        return switch (category) {
            case CATEGORY_TRADITIONAL -> "banco tradicional";
            case CATEGORY_DIGITAL -> "banco digital/instituição de pagamento";
            case CATEGORY_COOPERATIVE -> "cooperativa";
            case CATEGORY_FINANCIAL -> "financeira";
            default -> "indeterminada";
        };
    }

    private static boolean isAgencyUnlocated(String value) {
        String normalized = normalizeText(value);
        return normalized.isBlank() || UNLOCATED_AGENCY_VALUES.contains(normalized);
    }

    private static String normalizeCity(String value) {
        String normalized = normalizeText(value);
        if (normalized.contains("/")) {
            return normalized;
        }
        return normalized.replaceAll("\\s+", " ").trim();
    }

    private static String normalizeBankCode(String value) {
        if (value == null) {
            return "";
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() >= 3) {
            return digits.substring(digits.length() - 3);
        }
        return digits;
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static String cleanLabel(String value) {
        return value == null || value.isBlank() ? "Não identificado" : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record InstitutionProfile(String name, String bacenType, String category) {
    }

    private record DerivedAnalysis(String reliability,
                                   String reliabilityReason,
                                   String suggestion,
                                   String confidence,
                                   String evidence) {
    }

    @Data
    @Builder
    public static class PaymentPlacePreAnalysis {
        private String bankName;
        private String bacenInstitutionName;
        private String bacenInstitutionType;
        private String institutionCategory;
        private String geographicReliability;
        private String geographicReliabilityReason;
        private String automaticSuggestion;
        private String automaticConfidence;
        private String automaticEvidence;
    }
}
