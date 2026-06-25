package com.portal.serasa.application.service;

import com.portal.serasa.infrastructure.persistence.entity.PaymentPlaceEntryEntity;
import com.portal.serasa.infrastructure.persistence.repository.PaymentPlaceEntryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Score determinístico e explicável de sacado × cedente.
 *
 * <p>Roda depois das distâncias estarem calculadas (no import e após o
 * enriquecimento Bacen). Cada regra soma pontos para um dos lados e registra
 * um fator legível. A sugestão é o lado com maior pontuação; a confiança vem
 * da margem entre os scores e da confiabilidade geográfica.</p>
 */
@Component
@RequiredArgsConstructor
public class PaymentPlaceScorer {

    private static final String SACADO = "PROVAVEL_SACADO";
    private static final String CEDENTE = "PROVAVEL_CEDENTE";
    private static final String INCONCLUSIVO = "INCONCLUSIVO";

    private static final BigDecimal SAME_PLACE_KM = BigDecimal.valueOf(8);

    private final PaymentPlaceEntryJpaRepository entryRepository;
    private final com.portal.serasa.infrastructure.persistence.repository.PaymentPlacePatternJpaRepository patternRepository;

    public void apply(PaymentPlaceEntryEntity e) {
        int sacado = 0;
        int cedente = 0;
        boolean nonGeoSignal = false;
        List<String> factors = new ArrayList<>();

        // Limpa o snapshot do padrão (recalculado abaixo quando houver par).
        e.setLearnedPatternDecision(null);
        e.setLearnedPatternCount(null);
        e.setLearnedPatternTotal(null);

        String reliability = e.getGeographicReliability();
        boolean lowReliability = "BAIXA".equals(reliability) || "INDETERMINADA".equals(reliability);
        // Peso geográfico cheio só quando a praça é confiável.
        int geoFull = lowReliability ? 1 : 3;
        int geoPartial = lowReliability ? 1 : 2;

        BigDecimal dPayer = e.getDistanceAgencyPayerKm();
        BigDecimal dClient = e.getDistanceClientAgencyKm();

        if (dPayer != null && dClient != null) {
            if (dPayer.compareTo(SAME_PLACE_KM) <= 0 && dPayer.compareTo(dClient) < 0) {
                sacado += geoFull;
                factors.add("+%d sacado · agência na praça do sacado (%s km)".formatted(geoFull, dPayer));
            } else if (dClient.compareTo(SAME_PLACE_KM) <= 0 && dClient.compareTo(dPayer) < 0) {
                cedente += geoFull;
                factors.add("+%d cedente · agência na praça do cliente/cedente (%s km)".formatted(geoFull, dClient));
            } else if (dPayer.multiply(BigDecimal.valueOf(1.6)).compareTo(dClient) < 0) {
                sacado += geoPartial;
                factors.add("+%d sacado · agência mais próxima do sacado (%s vs %s km)".formatted(geoPartial, dPayer, dClient));
            } else if (dClient.multiply(BigDecimal.valueOf(1.6)).compareTo(dPayer) < 0) {
                cedente += geoPartial;
                factors.add("+%d cedente · agência mais próxima do cliente/cedente (%s vs %s km)".formatted(geoPartial, dClient, dPayer));
            } else {
                factors.add("0 · agência a distância semelhante de cliente e sacado");
            }
        } else if (e.getDistanceClientPayerKm() != null) {
            factors.add("0 · agência sem localização (relatório/Bacen); só dá para medir cedente↔sacado ("
                    + e.getDistanceClientPayerKm() + " km), que não indica o pagador");
        } else {
            factors.add("0 · sem distâncias suficientes para avaliar a praça");
        }

        // Complemento da ocorrência (sinal forte, independe da geografia).
        String complement = normalize(e.getOccurrenceComplement());
        boolean foraDoSacado = complement.contains("FORA DA PRACA DO SACADO");
        if (foraDoSacado) {
            cedente += 2;
            nonGeoSignal = true;
            factors.add("+2 cedente · complemento: pago fora da praça do sacado");
        }
        if (complement.contains("PRACA DO CLIENTE") || complement.contains("AGENCIA DO CLIENTE")) {
            cedente += 2;
            nonGeoSignal = true;
            factors.add("+2 cedente · complemento: vínculo com a praça/agência do cliente");
        }
        // "PRACA DO SACADO" só conta como sacado quando NÃO é "fora da praça do sacado".
        if (!foraDoSacado && (complement.contains("PRACA DO SACADO") || complement.contains("AGENCIA DO SACADO"))) {
            sacado += 2;
            nonGeoSignal = true;
            factors.add("+2 sacado · complemento: vínculo com a praça/agência do sacado");
        }

        // Recorrência histórica (decisões anteriores do mesmo sacado/cedente).
        if (e.getPayerDocument() != null && !e.getPayerDocument().isBlank()) {
            long prev = entryRepository.countByPayerDocumentAndAnalystDecision(e.getPayerDocument(), "SACADO");
            if (prev > 0) {
                sacado += 1;
                nonGeoSignal = true;
                factors.add("+1 sacado · sacado já classificado como sacado em %d título(s)".formatted(prev));
            }
        }
        if (e.getClientCode() != null && !e.getClientCode().isBlank()) {
            long prev = entryRepository.countByClientCodeAndAnalystDecision(e.getClientCode(), "CEDENTE");
            if (prev > 0) {
                cedente += 1;
                nonGeoSignal = true;
                factors.add("+1 cedente · cedente já classificado como cedente em %d título(s)".formatted(prev));
            }
        }

        // Padrão aprendido pelo par cedente×sacado: decisões passadas entre as MESMAS duas
        // empresas. Sinal forte e explicável — é o que faz o sistema "aprender" a cada importação.
        String cedDoc = PaymentPlacePatternService.digits(e.getClientDocument());
        String payDoc = PaymentPlacePatternService.digits(e.getPayerDocument());
        if (cedDoc != null && payDoc != null) {
            var pattern = patternRepository.findByClientDocumentAndPayerDocument(cedDoc, payDoc).orElse(null);
            if (pattern != null && pattern.isLocked() && pattern.getLockedDecision() != null) {
                int pts = 5;
                String locked = pattern.getLockedDecision();
                if ("CEDENTE".equals(locked)) {
                    cedente += pts;
                } else {
                    sacado += pts;
                }
                nonGeoSignal = true;
                e.setLearnedPatternDecision(locked);
                e.setLearnedPatternCount(Math.max(pattern.getCedenteCount(), pattern.getSacadoCount()));
                e.setLearnedPatternTotal(pattern.getTotalCount());
                factors.add("+%d %s · 🔒 padrão travado pelo analista para este par".formatted(pts, locked.toLowerCase(Locale.ROOT)));
            } else if (pattern != null && pattern.getTotalCount() >= 2) {
                int dom = Math.max(pattern.getCedenteCount(), pattern.getSacadoCount());
                String domDecision = pattern.getCedenteCount() >= pattern.getSacadoCount() ? "CEDENTE" : "SACADO";
                double share = (double) dom / pattern.getTotalCount();
                if (dom >= 2 && share >= 0.75) {
                    int pts = 2;
                    if (pattern.getTotalCount() >= 4) {
                        pts++;
                    }
                    if (share >= 0.999) {
                        pts++;
                    }
                    if ("CEDENTE".equals(domDecision)) {
                        cedente += pts;
                    } else {
                        sacado += pts;
                    }
                    nonGeoSignal = true;
                    e.setLearnedPatternDecision(domDecision);
                    e.setLearnedPatternCount(dom);
                    e.setLearnedPatternTotal(pattern.getTotalCount());
                    factors.add("+%d %s · par já decidido %s em %d/%d títulos (padrão aprendido)"
                            .formatted(pts, domDecision.toLowerCase(Locale.ROOT), domDecision, dom, pattern.getTotalCount()));
                }
            }
        }

        int margin = Math.abs(sacado - cedente);
        String suggestion;
        if ((sacado == 0 && cedente == 0) || margin == 0) {
            suggestion = INCONCLUSIVO;
        } else {
            suggestion = sacado > cedente ? SACADO : CEDENTE;
        }

        String confidence;
        if (INCONCLUSIVO.equals(suggestion)) {
            confidence = "BAIXA";
        } else if (margin >= 3) {
            confidence = "ALTA";
        } else if (margin == 2) {
            confidence = "MEDIA";
        } else {
            confidence = "BAIXA";
        }
        // Sem sinal não-geográfico e praça pouco confiável → não confiar alto.
        if (!nonGeoSignal && lowReliability && "ALTA".equals(confidence)) {
            confidence = "MEDIA";
        }

        if (e.getGeographicReliabilityReason() != null && !e.getGeographicReliabilityReason().isBlank()) {
            factors.add(e.getGeographicReliabilityReason());
        }

        e.setScoreSacado(sacado);
        e.setScoreCedente(cedente);
        e.setAutomaticSuggestion(suggestion);
        e.setAutomaticConfidence(confidence);
        e.setAutomaticEvidence(String.join("\n", factors));
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
