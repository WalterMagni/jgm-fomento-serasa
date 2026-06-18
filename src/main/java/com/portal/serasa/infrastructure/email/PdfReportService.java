package com.portal.serasa.infrastructure.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.portal.serasa.domain.model.CreditAnalysis;
import com.portal.serasa.infrastructure.integration.gemini.GeminiAnalysisResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class PdfReportService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color PRIMARY = new Color(97, 32, 53);   // #612035
    private static final Color LIGHT_GRAY = new Color(245, 245, 245);
    private static final Color DARK_GRAY = new Color(55, 65, 81);

    public byte[] generate(CreditAnalysis analysis, GeminiAnalysisResult aiResult) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 50, 50);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            addHeader(doc, analysis);
            addCompanyInfo(doc, analysis);
            addNegativeData(doc, analysis);
            if (aiResult != null && aiResult.isAvailable()) {
                addAiAnalysis(doc, aiResult);
            }
            addFooter(doc, analysis);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Erro ao gerar PDF para CNPJ={}", analysis.getCnpj(), e);
            return new byte[0];
        }
    }

    private void addHeader(Document doc, CreditAnalysis a) throws DocumentException {
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Font.BOLD, Color.WHITE);
        Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, new Color(255, 200, 200));

        PdfPTable header = new PdfPTable(1);
        header.setWidthPercentage(100);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(PRIMARY);
        cell.setPadding(20);
        cell.setBorder(Rectangle.NO_BORDER);

        Paragraph p = new Paragraph();
        p.add(new Chunk("JGM Fomento · Portal Serasa\n", subFont));
        p.add(new Chunk("Relatório de Análise de Crédito", titleFont));
        cell.addElement(p);
        header.addCell(cell);

        doc.add(header);
        doc.add(Chunk.NEWLINE);
    }

    private void addCompanyInfo(Document doc, CreditAnalysis a) throws DocumentException {
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.BOLD, PRIMARY);
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.BOLD, Color.GRAY);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, DARK_GRAY);
        Font valueBoldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, DARK_GRAY);

        doc.add(new Paragraph("IDENTIFICAÇÃO DA EMPRESA", sectionFont));
        doc.add(new LineSeparator(1f, 100f, PRIMARY, Element.ALIGN_LEFT, -2));
        doc.add(Chunk.NEWLINE);

        PdfPTable table = new PdfPTable(new float[]{1, 1});
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);

        addInfoRow(table, "RAZÃO SOCIAL", a.getCompanyName() != null ? a.getCompanyName() : "-", labelFont, valueBoldFont);
        addInfoRow(table, "CNPJ", formatCnpj(a.getCnpj()), labelFont, valueFont);
        addInfoRow(table, "DATA DA CONSULTA", a.getConsultaEm() != null ? a.getConsultaEm().format(FMT) : "-", labelFont, valueFont);
        addInfoRow(table, "SCORE SERASA", a.getScore() != null ? String.valueOf(a.getScore()) : "-", labelFont, valueFont);

        String riskLabel = a.getRiskClass() != null ? "Classe " + a.getRiskClass() : "-";
        addInfoRow(table, "CLASSE DE RISCO", riskLabel, labelFont, valueFont);

        String cedente = a.getVisaoCedente() != null ? a.getVisaoCedente() : "PENDENTE";
        Font cedenteFont = "SIM".equals(cedente)
                ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, new Color(22, 101, 52))
                : FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, DARK_GRAY);
        addInfoRow(table, "VISÃO CEDENTE", cedente, labelFont, cedenteFont);

        doc.add(table);
        doc.add(Chunk.NEWLINE);
    }

    private void addNegativeData(Document doc, CreditAnalysis a) throws DocumentException {
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.BOLD, PRIMARY);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, DARK_GRAY);

        doc.add(new Paragraph("DADOS NEGATIVOS", sectionFont));
        doc.add(new LineSeparator(1f, 100f, PRIMARY, Element.ALIGN_LEFT, -2));
        doc.add(Chunk.NEWLINE);

        JsonNode neg = a.getNegativeSummary();
        if (neg == null) {
            doc.add(new Paragraph("Sem dados de negativações.", valueFont));
            doc.add(Chunk.NEWLINE);
            return;
        }

        PdfPTable table = new PdfPTable(new float[]{2, 1, 1.5f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);

        // Header row
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.BOLD, Color.WHITE);
        addTableHeaderCell(table, "TIPO", headerFont);
        addTableHeaderCell(table, "QTD", headerFont);
        addTableHeaderCell(table, "VALOR (R$)", headerFont);

        addNegRow(table, "PEFIN", neg, "pefin", valueFont);
        addNegRow(table, "REFIN", neg, "refin", valueFont);
        addNegRow(table, "Cheques", neg, "check", valueFont);
        addNegRow(table, "Protestos", neg, "notary", valueFont);
        addNegRow(table, "Cobranças", neg, "collectionRecords", valueFont);

        JsonNode inq = a.getInquiryHistory();
        if (inq != null) {
            int bankrupts = inq.path("bankrupts").path("summary").path("count").asInt(0);
            int acoes = inq.path("judgementFilings").path("summary").path("count").asInt(0);
            addSimpleRow(table, "Falências/Concordatas", bankrupts, valueFont);
            addSimpleRow(table, "Ações Judiciais", acoes, valueFont);
        }

        doc.add(table);
        doc.add(Chunk.NEWLINE);
    }

    private void addAiAnalysis(Document doc, GeminiAnalysisResult ai) throws DocumentException {
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.BOLD, PRIMARY);
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.BOLD, new Color(3, 105, 161));
        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, DARK_GRAY);
        Font disclaimerFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Font.ITALIC, Color.GRAY);
        Font bulletFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, DARK_GRAY);

        doc.add(new Paragraph("ANÁLISE DE CRÉDITO COM IA (GEMINI)", sectionFont));
        doc.add(new LineSeparator(1f, 100f, PRIMARY, Element.ALIGN_LEFT, -2));
        doc.add(Chunk.NEWLINE);

        // Disclaimer
        PdfPTable disclaimer = new PdfPTable(1);
        disclaimer.setWidthPercentage(100);
        PdfPCell dc = new PdfPCell(new Paragraph(
                "AVISO: Esta análise foi gerada por Inteligência Artificial com base nos dados disponibilizados pela Serasa Experian. " +
                "Os dados do Serasa podem conter imprecisões ou omissões — por exemplo, empresas que constam sem funcionários no cadastro " +
                "mas que efetivamente possuem colaboradores. Esta análise é uma ferramenta auxiliar e não substitui a avaliação humana.",
                disclaimerFont));
        dc.setBackgroundColor(new Color(255, 251, 235));
        dc.setBorderColor(new Color(252, 211, 77));
        dc.setPadding(8);
        disclaimer.addCell(dc);
        doc.add(disclaimer);
        doc.add(Chunk.NEWLINE);

        // Risk + Recomendação
        if (ai.getNivelRisco() != null || ai.getRecomendacao() != null) {
            PdfPTable badges = new PdfPTable(2);
            badges.setWidthPercentage(60);
            badges.setHorizontalAlignment(Element.ALIGN_LEFT);

            Font badgeFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, Color.WHITE);
            Color riskColor = getRiskColor(ai.getNivelRisco());
            Color recColor = getRecColor(ai.getRecomendacao());

            PdfPCell riskCell = new PdfPCell(new Paragraph(formatRisk(ai.getNivelRisco()), badgeFont));
            riskCell.setBackgroundColor(riskColor);
            riskCell.setPadding(8);
            riskCell.setBorder(Rectangle.NO_BORDER);
            badges.addCell(riskCell);

            PdfPCell recCell = new PdfPCell(new Paragraph(formatRec(ai.getRecomendacao()), badgeFont));
            recCell.setBackgroundColor(recColor);
            recCell.setPadding(8);
            recCell.setBorder(Rectangle.NO_BORDER);
            badges.addCell(recCell);

            doc.add(badges);
            doc.add(Chunk.NEWLINE);
        }

        // Parecer
        if (ai.getParecer() != null && !ai.getParecer().isBlank()) {
            doc.add(new Paragraph("PARECER GERAL", labelFont));
            doc.add(new Paragraph(ai.getParecer(), bodyFont));
            doc.add(Chunk.NEWLINE);
        }

        // Visão Cedente
        if (ai.getVisaoCedente() != null && !ai.getVisaoCedente().isBlank()) {
            doc.add(new Paragraph("VISÃO CEDENTE (FACTORING)", labelFont));
            doc.add(new Paragraph(ai.getVisaoCedente(), bodyFont));
            doc.add(Chunk.NEWLINE);
        }

        // Pontos fortes
        if (ai.getPontosFortes() != null && ai.getPontosFortes().length > 0) {
            doc.add(new Paragraph("PONTOS POSITIVOS", labelFont));
            for (String p : ai.getPontosFortes()) {
                doc.add(new Paragraph("• " + p, bulletFont));
            }
            doc.add(Chunk.NEWLINE);
        }

        // Pontos de atenção
        if (ai.getPontosAtencao() != null && ai.getPontosAtencao().length > 0) {
            doc.add(new Paragraph("PONTOS DE ATENÇÃO", labelFont));
            for (String p : ai.getPontosAtencao()) {
                doc.add(new Paragraph("• " + p, bulletFont));
            }
            doc.add(Chunk.NEWLINE);
        }
    }

    private void addFooter(Document doc, CreditAnalysis a) throws DocumentException {
        Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, Color.GRAY);
        doc.add(new LineSeparator(0.5f, 100f, Color.LIGHT_GRAY, Element.ALIGN_LEFT, 0));
        doc.add(Chunk.NEWLINE);
        doc.add(new Paragraph(
                "Relatório gerado pelo Portal Serasa JGM Fomento em " +
                java.time.LocalDateTime.now().format(FMT) + ".\n" +
                "Os dados apresentados são baseados nas informações fornecidas pela Serasa Experian e podem não refletir " +
                "a situação atual da empresa. Esta análise tem caráter informativo e auxiliar.",
                footerFont));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void addInfoRow(PdfPTable table, String label, String value, Font lf, Font vf) {
        PdfPCell lCell = new PdfPCell(new Paragraph(label, lf));
        lCell.setBorder(Rectangle.BOTTOM);
        lCell.setBorderColor(new Color(229, 231, 235));
        lCell.setPadding(6);
        lCell.setBackgroundColor(LIGHT_GRAY);

        PdfPCell vCell = new PdfPCell(new Paragraph(value, vf));
        vCell.setBorder(Rectangle.BOTTOM);
        vCell.setBorderColor(new Color(229, 231, 235));
        vCell.setPadding(6);

        table.addCell(lCell);
        table.addCell(vCell);
    }

    private void addTableHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, font));
        cell.setBackgroundColor(PRIMARY);
        cell.setPadding(6);
        cell.setBorder(Rectangle.NO_BORDER);
        table.addCell(cell);
    }

    private void addNegRow(PdfPTable table, String label, JsonNode neg, String key, Font vf) {
        int count = neg.path(key).path("summary").path("count").asInt(0);
        double balance = neg.path(key).path("summary").path("balance").asDouble(0);

        Font countFont = count > 0
                ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.BOLD, new Color(185, 28, 28))
                : vf;

        table.addCell(styledCell(label, vf));
        table.addCell(styledCell(String.valueOf(count), countFont));
        table.addCell(styledCell(count > 0 ? formatCurrency(balance) : "-", countFont));
    }

    private void addSimpleRow(PdfPTable table, String label, int count, Font vf) {
        Font countFont = count > 0
                ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.BOLD, new Color(185, 28, 28))
                : vf;
        table.addCell(styledCell(label, vf));
        table.addCell(styledCell(String.valueOf(count), countFont));
        table.addCell(styledCell("-", vf));
    }

    private PdfPCell styledCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, font));
        cell.setPadding(5);
        cell.setBorderColor(new Color(229, 231, 235));
        return cell;
    }

    private String formatCnpj(String cnpj) {
        if (cnpj == null || cnpj.length() < 14) return cnpj != null ? cnpj : "-";
        return cnpj.replaceAll("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
    }

    private String formatCurrency(double value) {
        return String.format("R$ %,.2f", value).replace(",", "X").replace(".", ",").replace("X", ".");
    }

    private Color getRiskColor(String risk) {
        if (risk == null) return new Color(107, 114, 128);
        return switch (risk) {
            case "BAIXO" -> new Color(21, 128, 61);
            case "MODERADO" -> new Color(180, 83, 9);
            case "ALTO" -> new Color(194, 65, 12);
            case "MUITO_ALTO" -> new Color(185, 28, 28);
            default -> new Color(107, 114, 128);
        };
    }

    private Color getRecColor(String rec) {
        if (rec == null) return new Color(107, 114, 128);
        return switch (rec) {
            case "APROVADO" -> new Color(21, 128, 61);
            case "CONDICIONAL" -> new Color(180, 83, 9);
            case "NEGADO" -> new Color(185, 28, 28);
            default -> new Color(107, 114, 128);
        };
    }

    private String formatRisk(String risk) {
        if (risk == null) return "—";
        return switch (risk) {
            case "BAIXO" -> "Baixo Risco";
            case "MODERADO" -> "Risco Moderado";
            case "ALTO" -> "Alto Risco";
            case "MUITO_ALTO" -> "Muito Alto Risco";
            default -> risk;
        };
    }

    private String formatRec(String rec) {
        if (rec == null) return "—";
        return switch (rec) {
            case "APROVADO" -> "Aprovado";
            case "CONDICIONAL" -> "Condicional";
            case "NEGADO" -> "Negado";
            default -> rec;
        };
    }
}
