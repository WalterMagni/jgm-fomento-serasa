package com.portal.serasa.application.service;

import lombok.Builder;
import lombok.Data;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PaymentPlacePdfParser {

    public static final String SECTION_AUDIT = "Auditoria Eletrônica";
    public static final String SECTION_UNLOCATED_AGENCIES = "Agências Não Localizadas";

    private static final Pattern SECTION_PATTERN = Pattern.compile(
            "Retorno Bancário - (?<section>Auditoria Eletrônica|Agências Não Localizadas)");
    private static final Pattern ROW_1_PATTERN = Pattern.compile(
            "^\\s*(?<externalId>\\d{6})\\s+(?<clientCode>\\d{4,6})\\s+(?<titleNumber>\\S+)\\s+"
                    + "(?<dueDate>\\d{2}/\\d{2}/\\d{4})\\s+(?<titleValue>[\\d.]+,\\d{2})\\s+"
                    + "(?<payer>.+?)\\s+(?<paidValue>[\\d.]+,\\d{2})\\s+(?<occurrence>\\d{3}-.+?)\\s*$");
    private static final Pattern ROW_2_PATTERN = Pattern.compile(
            "^\\s*(?<clientCity>.+?/\\p{Lu}{2})\\s+"
                    + "(?<agencyCityPdf>AGÊNCIA NÃO LOCALIZADA|.+?/\\p{Lu}{2})\\s+"
                    + "(?<payerCity>.+?/\\p{Lu}{2})\\s+"
                    + "(?<bankAgency>\\d{3}/\\d{5})\\s+(?<occurrenceComplement>\\d{3}-.+?)\\s*$");
    private static final Pattern PAYER_PATTERN = Pattern.compile(
            "(?<document>(?:\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})|(?:\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}))\\s+(?<name>.+)");

    public List<PaymentPlaceParsedEntry> parse(InputStream inputStream) throws IOException {
        String text = extractText(inputStream);
        String[] lines = text.split("\\R");
        List<PaymentPlaceParsedEntry> entries = new ArrayList<>();
        String currentSection = null;

        for (int i = 0; i < lines.length; i++) {
            Matcher sectionMatcher = SECTION_PATTERN.matcher(lines[i]);
            if (sectionMatcher.find()) {
                currentSection = sectionMatcher.group("section");
                continue;
            }

            if (currentSection == null) {
                continue;
            }
            if (lines[i].trim().startsWith("Totais->")) {
                currentSection = null;
                continue;
            }

            Matcher row1 = ROW_1_PATTERN.matcher(lines[i]);
            if (!row1.matches()) {
                continue;
            }

            int row2Index = nextContentLine(lines, i + 1);
            if (row2Index < 0) {
                continue;
            }

            Matcher row2 = ROW_2_PATTERN.matcher(lines[row2Index]);
            if (!row2.matches()) {
                continue;
            }

            String payer = row1.group("payer").trim();
            String payerDocument = null;
            String payerName = payer;
            Matcher payerMatcher = PAYER_PATTERN.matcher(payer);
            if (payerMatcher.matches()) {
                payerDocument = payerMatcher.group("document");
                payerName = payerMatcher.group("name").trim();
            }

            String bankAgency = row2.group("bankAgency");
            String[] bankAgencyParts = bankAgency.split("/");

            entries.add(PaymentPlaceParsedEntry.builder()
                    .section(currentSection)
                    .externalId(row1.group("externalId"))
                    .clientCode(row1.group("clientCode"))
                    .titleNumber(row1.group("titleNumber"))
                    .dueDate(row1.group("dueDate"))
                    .titleValue(row1.group("titleValue"))
                    .paidValue(row1.group("paidValue"))
                    .occurrence(row1.group("occurrence").trim())
                    .payerDocument(payerDocument)
                    .payerName(payerName)
                    .clientCity(row2.group("clientCity").trim())
                    .agencyCityPdf(row2.group("agencyCityPdf").trim())
                    .payerCity(row2.group("payerCity").trim())
                    .bankAgency(bankAgency)
                    .bankCode(bankAgencyParts[0])
                    .agencyCode(bankAgencyParts[1])
                    .occurrenceComplement(row2.group("occurrenceComplement").trim())
                    .build());
        }

        return entries;
    }

    private String extractText(InputStream inputStream) throws IOException {
        byte[] bytes = inputStream.readAllBytes();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private int nextContentLine(String[] lines, int startIndex) {
        for (int i = startIndex; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.isBlank() || trimmed.chars().allMatch(ch -> ch == '-')) {
                continue;
            }
            return i;
        }
        return -1;
    }

    @Data
    @Builder
    public static class PaymentPlaceParsedEntry {
        private String section;
        private String externalId;
        private String clientCode;
        private String titleNumber;
        private String dueDate;
        private String titleValue;
        private String paidValue;
        private String occurrence;
        private String payerDocument;
        private String payerName;
        private String clientCity;
        private String agencyCityPdf;
        private String payerCity;
        private String bankAgency;
        private String bankCode;
        private String agencyCode;
        private String occurrenceComplement;
    }
}
