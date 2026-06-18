package com.portal.serasa.application.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Geocodificação offline por município usando os centroides do IBGE
 * (src/main/resources/data/municipios-ibge.csv: nome,uf,latitude,longitude).
 *
 * <p>Granularidade é o município (Cidade/UF), que é tudo que o relatório
 * bancário fornece. Sem chamada externa, sem API key, sem custo.</p>
 */
@Slf4j
@Component
public class MunicipalityGeocoder {

    private static final String DATASET = "data/municipios-ibge.csv";
    private static final int EARTH_RADIUS_KM = 6371;

    private final Map<String, Coordinates> centroidsByKey = new HashMap<>();

    @PostConstruct
    void load() {
        ClassPathResource resource = new ClassPathResource(DATASET);
        try (InputStream inputStream = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length != 4) {
                    continue;
                }
                String key = buildKey(parts[0], parts[1]);
                if (key == null) {
                    continue;
                }
                try {
                    centroidsByKey.put(key, new Coordinates(
                            new BigDecimal(parts[2].trim()),
                            new BigDecimal(parts[3].trim())));
                } catch (NumberFormatException ignored) {
                    // linha malformada — ignora
                }
            }
            log.info("MunicipalityGeocoder carregou {} municípios", centroidsByKey.size());
        } catch (Exception e) {
            log.error("Falha ao carregar dataset de municípios IBGE ({})", DATASET, e);
        }
    }

    /**
     * Resolve as coordenadas a partir de uma string "Cidade/UF" do relatório.
     * Retorna vazio se o município não for identificado ou o valor for inválido
     * (ex.: "AGÊNCIA NÃO LOCALIZADA").
     */
    public Optional<Coordinates> resolve(String cityWithUf) {
        if (cityWithUf == null) {
            return Optional.empty();
        }
        int slash = cityWithUf.lastIndexOf('/');
        if (slash <= 0 || slash == cityWithUf.length() - 1) {
            return Optional.empty();
        }
        String name = cityWithUf.substring(0, slash);
        String uf = cityWithUf.substring(slash + 1);
        String key = buildKey(name, uf);
        return key == null ? Optional.empty() : Optional.ofNullable(centroidsByKey.get(key));
    }

    /**
     * Distância em km entre dois pontos (haversine), arredondada a 1 casa.
     * Retorna {@code null} se algum dos pontos for nulo.
     */
    public BigDecimal distanceKm(Coordinates a, Coordinates b) {
        if (a == null || b == null) {
            return null;
        }
        double lat1 = a.latitude().doubleValue();
        double lon1 = a.longitude().doubleValue();
        double lat2 = b.latitude().doubleValue();
        double lon2 = b.longitude().doubleValue();

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
        return BigDecimal.valueOf(EARTH_RADIUS_KM * c).setScale(1, RoundingMode.HALF_UP);
    }

    private static String buildKey(String name, String uf) {
        String normalizedName = normalize(name);
        String normalizedUf = normalize(uf);
        if (normalizedName.isEmpty() || normalizedUf.length() != 2) {
            return null;
        }
        return normalizedName + "/" + normalizedUf;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String stripped = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        // Pontuação (apóstrofo, hífen) vira espaço para "Santa Bárbara d'Oeste" (IBGE)
        // casar com "SANTA BARBARA D OESTE" (relatório). Mantém só letras/dígitos/espaço.
        return stripped.toUpperCase()
                .replaceAll("[^\\p{Alnum} ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public record Coordinates(BigDecimal latitude, BigDecimal longitude) {
    }
}
