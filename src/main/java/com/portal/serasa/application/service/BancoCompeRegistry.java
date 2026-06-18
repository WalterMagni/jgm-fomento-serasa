package com.portal.serasa.application.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Mapa COMPE → CNPJ base do banco (src/main/resources/data/bancos-compe.csv:
 * compe,cnpj_base,nome). Usado para desambiguar a agência no dataset do Bacen,
 * que é chaveado por CnpjBase + CodigoCompe (número da agência).
 */
@Slf4j
@Component
public class BancoCompeRegistry {

    private static final String DATASET = "data/bancos-compe.csv";

    private final Map<String, String> cnpjBaseByCompe = new HashMap<>();
    private final Map<String, String> nameByCompe = new HashMap<>();

    @PostConstruct
    void load() {
        ClassPathResource resource = new ClassPathResource(DATASET);
        try (InputStream inputStream = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", 3);
                if (parts.length < 2) {
                    continue;
                }
                String compe = normalizeCompe(parts[0]);
                String cnpjBase = parts[1].trim();
                if (compe == null || cnpjBase.length() != 8) {
                    continue;
                }
                cnpjBaseByCompe.put(compe, cnpjBase);
                if (parts.length == 3) {
                    nameByCompe.put(compe, parts[2].trim());
                }
            }
            log.info("BancoCompeRegistry carregou {} bancos", cnpjBaseByCompe.size());
        } catch (Exception e) {
            log.error("Falha ao carregar dataset COMPE→CNPJ ({})", DATASET, e);
        }
    }

    public Optional<String> cnpjBase(String compeCode) {
        String compe = normalizeCompe(compeCode);
        return compe == null ? Optional.empty() : Optional.ofNullable(cnpjBaseByCompe.get(compe));
    }

    public Optional<String> bankName(String compeCode) {
        String compe = normalizeCompe(compeCode);
        return compe == null ? Optional.empty() : Optional.ofNullable(nameByCompe.get(compe));
    }

    /** Normaliza para 3 dígitos com zeros à esquerda (ex.: "1" → "001", "208" → "208"). */
    static String normalizeCompe(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.isEmpty() || digits.length() > 3) {
            return null;
        }
        return String.format("%03d", Integer.parseInt(digits));
    }
}
