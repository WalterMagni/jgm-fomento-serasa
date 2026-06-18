package com.portal.serasa.infrastructure.integration.bacen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.portal.serasa.application.service.MunicipalityGeocoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Geocodificação street-level por endereço completo via Nominatim (OpenStreetMap).
 * Usado para precisão intramunicipal (ex.: agência e sacado na mesma cidade) que o
 * centroide do município não captura.
 *
 * <p>Respeita a política de uso do Nominatim público: máximo 1 requisição por segundo
 * (throttle global) e resultados cacheados por query normalizada. Falha ou ausência de
 * resultado retorna vazio para o chamador cair no fallback (centroide do município).</p>
 */
@Slf4j
@Component
public class AddressGeocoder {

    private static final long MIN_INTERVAL_MS = 1100;

    private final RestClient nominatimRestClient;
    private final boolean enabled;
    private final Map<String, Optional<MunicipalityGeocoder.Coordinates>> cache = new ConcurrentHashMap<>();
    private final Object throttleLock = new Object();
    private long lastCallMillis = 0;

    public AddressGeocoder(@Qualifier("nominatimRestClient") RestClient nominatimRestClient,
                           @Value("${nominatim.enabled:true}") boolean enabled) {
        this.nominatimRestClient = nominatimRestClient;
        this.enabled = enabled;
    }

    /**
     * Resolve coordenadas street-level a partir das partes do endereço. Retorna vazio
     * quando desabilitado, sem dados suficientes ou quando o Nominatim não encontra.
     */
    public Optional<MunicipalityGeocoder.Coordinates> geocode(String street, String number, String district,
                                                              String city, String uf, String cep) {
        if (!enabled) {
            return Optional.empty();
        }
        String query = buildQuery(street, number, district, city, uf, cep);
        if (query == null) {
            return Optional.empty();
        }
        return cache.computeIfAbsent(query, this::lookup);
    }

    private Optional<MunicipalityGeocoder.Coordinates> lookup(String query) {
        try {
            throttle();
            List<NominatimResult> results = nominatimRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", query)
                            .queryParam("format", "json")
                            .queryParam("countrycodes", "br")
                            .queryParam("limit", "1")
                            .build())
                    .retrieve()
                    .body(NominatimResultList.class);
            if (results == null || results.isEmpty()) {
                return Optional.empty();
            }
            NominatimResult top = results.get(0);
            if (top.lat() == null || top.lon() == null) {
                return Optional.empty();
            }
            return Optional.of(new MunicipalityGeocoder.Coordinates(
                    new BigDecimal(top.lat()), new BigDecimal(top.lon())));
        } catch (Exception e) {
            log.warn("Falha ao geocodificar endereço no Nominatim (q='{}'): {}", query, e.getMessage());
            return Optional.empty();
        }
    }

    /** Garante o intervalo mínimo entre chamadas exigido pela política do Nominatim. */
    private void throttle() {
        synchronized (throttleLock) {
            long now = System.currentTimeMillis();
            long wait = lastCallMillis + MIN_INTERVAL_MS - now;
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            lastCallMillis = System.currentTimeMillis();
        }
    }

    private String buildQuery(String street, String number, String district, String city, String uf, String cep) {
        StringBuilder sb = new StringBuilder();
        appendPart(sb, street);
        appendPart(sb, number);
        appendPart(sb, district);
        appendPart(sb, city);
        appendPart(sb, uf);
        appendPart(sb, cep);
        // Sem logradouro ou cidade o resultado seria apenas o centroide — não vale a chamada.
        if (isBlank(street) || isBlank(city)) {
            return null;
        }
        sb.append(", Brasil");
        return sb.toString();
    }

    private void appendPart(StringBuilder sb, String part) {
        if (isBlank(part)) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(part.trim());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NominatimResultList extends java.util.ArrayList<NominatimResult> {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NominatimResult(@JsonProperty("lat") String lat, @JsonProperty("lon") String lon) {
    }
}
