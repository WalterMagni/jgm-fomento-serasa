package com.portal.serasa.infrastructure.integration.csv;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import com.portal.serasa.domain.model.Client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Leitor de CSV para importação de clientes.
 * Mapeia colunas por índice conforme layout do ERP legado.
 */
@Component
@Slf4j
public class CsvClientReader {

    private static final char SEPARATOR = ';';
    private static final char QUOTE = '"';

    // Índices das colunas (0-based)
    private static final int COL_CODIGO_CLIENTE = 0;
    private static final int COL_CNPJ_CPF = 1;
    private static final int COL_NOME_RAZAO_SOCIAL = 4;
    private static final int COL_ENDERECO_CEP = 5;
    private static final int COL_ENDERECO_LOGRADOURO = 6;
    private static final int COL_ENDERECO_NUMERO = 7;
    private static final int COL_ENDERECO_COMPLEMENTO = 8;
    private static final int COL_ENDERECO_BAIRRO = 9;
    private static final int COL_MUNICIPIO = 11;
    private static final int COL_UF = 12;
    private static final int COL_TELEFONE = 13;
    private static final int COL_CELULAR1 = 14;
    private static final int COL_CELULAR2 = 15;
    private static final int COL_EMAIL = 16;

    private static final int DOCUMENT_PADDING_LENGTH = 14;

    /**
     * Lê o CSV e retorna lista de clientes parseados (sem persistir).
     * Exibe resumo dos dados para preview antes da importação.
     */
    public List<Client> readAndParse(InputStream inputStream) throws IOException, CsvException {
        var parser = new CSVParserBuilder()
                .withSeparator(SEPARATOR)
                .withQuoteChar(QUOTE)
                .build();

        try (var reader = new CSVReaderBuilder(
                new InputStreamReader(inputStream, java.nio.charset.Charset.forName("ISO-8859-1")))
                .withCSVParser(parser)
                .build()) {

            List<String[]> allRows = reader.readAll();
            if (allRows.isEmpty()) {
                log.info("CSV vazio");
                return List.of();
            }

            // Pula cabeçalho
            List<String[]> dataRows = allRows.size() > 1
                    ? allRows.subList(1, allRows.size())
                    : List.of();

            List<Client> clients = new ArrayList<>();
            int rowNum = 2; // 1=cabeçalho, 2=primeira linha de dados

            for (String[] row : dataRows) {
                try {
                    Client client = parseRow(row, rowNum);
                    if (client != null) {
                        clients.add(client);
                    }
                } catch (Exception e) {
                    log.warn("Linha {} ignorada por erro: {} - Dados: {}", rowNum, e.getMessage(),
                            Arrays.toString(row));
                }
                rowNum++;
            }

            log.info("CSV parseado: {} clientes extraídos de {} linhas", clients.size(), dataRows.size());
            return clients;
        }
    }

    /**
     * Exibe preview dos dados parseados (para uso em logs ou endpoint de
     * visualização).
     */
    public CsvClientPreview createPreview(List<Client> clients, int maxItems) {
        List<ClientPreviewItem> items = clients.stream()
                .limit(maxItems)
                .map(c -> new ClientPreviewItem(
                        c.getDocumentNumber(),
                        c.getName(),
                        c.getEmail(),
                        c.getPhones() != null ? c.getPhones() : List.of()))
                .toList();

        return CsvClientPreview.builder()
                .totalRows(clients.size())
                .previewRows(items)
                .build();
    }

    private Client parseRow(String[] row, int rowNum) {
        if (row.length <= COL_EMAIL) {
            log.debug("Linha {} ignorada: colunas insuficientes ({})", rowNum, row.length);
            return null;
        }

        String documentNumber = normalizeDocument(getCell(row, COL_CNPJ_CPF));
        if (documentNumber == null || documentNumber.isBlank()) {
            log.debug("Linha {} ignorada: documentNumber vazio", rowNum);
            return null;
        }

        String name = sanitize(getCell(row, COL_NOME_RAZAO_SOCIAL));
        String email = sanitize(getCell(row, COL_EMAIL));
        Set<String> phones = collectPhones(row);

        return Client.builder()
                .documentNumber(documentNumber)
                .clientCode(normalizeClientCode(getCell(row, COL_CODIGO_CLIENTE)))
                .name(name)
                .email(email)
                .phones(new ArrayList<>(phones))
                .addressZip(normalizeZip(getCell(row, COL_ENDERECO_CEP)))
                .addressStreet(sanitize(getCell(row, COL_ENDERECO_LOGRADOURO)))
                .addressNumber(sanitize(getCell(row, COL_ENDERECO_NUMERO)))
                .addressComplement(sanitize(getCell(row, COL_ENDERECO_COMPLEMENTO)))
                .addressDistrict(sanitize(getCell(row, COL_ENDERECO_BAIRRO)))
                .addressCity(sanitize(getCell(row, COL_MUNICIPIO)))
                .addressUf(sanitize(getCell(row, COL_UF)))
                .build();
    }

    private String getCell(String[] row, int index) {
        if (index < 0 || index >= row.length) {
            return "";
        }
        String value = row[index];
        return value != null ? value.trim() : "";
    }

    /**
     * Remove pontuação e aplica LPAD com zeros à esquerda até 14 dígitos.
     * Se tiver mais de 14 dígitos, mantém os primeiros 14.
     */
    private String normalizeDocument(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (digits.length() > DOCUMENT_PADDING_LENGTH) {
            digits = digits.substring(0, DOCUMENT_PADDING_LENGTH);
        }
        return String.format("%" + DOCUMENT_PADDING_LENGTH + "s", digits).replace(' ', '0');
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /** Código canônico do cliente: dígitos sem zeros à esquerda (casa com o "000693" do PDF). */
    private String normalizeClientCode(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "").replaceFirst("^0+", "");
        return digits.isEmpty() ? null : digits;
    }

    /** Normaliza CEP para o formato 00000-000; retorna null se não tiver 8 dígitos. */
    private String normalizeZip(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() != 8) {
            return null;
        }
        return digits.substring(0, 5) + "-" + digits.substring(5);
    }

    private Set<String> collectPhones(String[] row) {
        Set<String> phones = new LinkedHashSet<>();
        for (int col : new int[] { COL_TELEFONE, COL_CELULAR1, COL_CELULAR2 }) {
            String raw = getCell(row, col);
            if (raw != null && !raw.isBlank()) {
                String cleaned = raw.replaceAll("\\D", "");
                if (cleaned.length() >= 10) { // telefone válido tem ao menos 10 dígitos
                    phones.add(cleaned);
                }
            }
        }
        return phones;
    }

    /**
     * DTO para preview dos dados do CSV.
     */
    @lombok.Data
    @lombok.Builder
    public static class CsvClientPreview {
        private final int totalRows;
        private final List<ClientPreviewItem> previewRows;
    }

    public record ClientPreviewItem(String documentNumber, String name, String email, List<String> phones) {
    }
}
