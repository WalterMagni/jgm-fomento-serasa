package com.portal.serasa.infrastructure.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.serasa.domain.model.CreditAnalysis;
import com.portal.serasa.infrastructure.integration.gemini.GeminiAnalysisResult;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final PdfReportService pdfReportService;
    private final ObjectMapper objectMapper;

    @Value("${email.cedente.from}")
    private String emailFrom;

    @Value("${email.cedente.to}")
    private String emailTo;

    @Value("${email.cedente.enabled:true}")
    private boolean enabled;

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public void notificarCedente(CreditAnalysis analysis, MultipartFile customPdf) {
        byte[] pdfBytes = null;
        String fileName = null;

        if (customPdf != null && !customPdf.isEmpty()) {
            try {
                pdfBytes = customPdf.getBytes();
                fileName = customPdf.getOriginalFilename();
            } catch (IOException e) {
                throw new IllegalStateException("Falha ao ler o PDF do relatório para envio do e-mail.", e);
            }
        }

        enviarEmailCedente(analysis, pdfBytes, fileName);
    }

    @Async
    public void notificarCedenteAsync(CreditAnalysis analysis) {
        try {
            enviarEmailCedente(analysis, null, null);
        } catch (Exception e) {
            log.error("Falha ao enviar e-mail assíncrono de Visão Cedente para CNPJ={}", analysis.getCnpj(), e);
        }
    }

    private void enviarEmailCedente(CreditAnalysis analysis, byte[] customPdfBytes, String customPdfName) {
        if (!enabled) {
            log.info("E-mail desabilitado. Pulando notificação para CNPJ={}", analysis.getCnpj());
            return;
        }

        GeminiAnalysisResult aiResult = parseAiAnalysis(analysis.getAiAnalysis());

        try {
            byte[] pdfBytes;
            String fileName = buildPdfFileName(analysis);

            if (customPdfBytes != null && customPdfBytes.length > 0) {
                pdfBytes = customPdfBytes;
                if (customPdfName != null && !customPdfName.isBlank()) {
                    fileName = customPdfName;
                }
            } else {
                pdfBytes = pdfReportService.generate(analysis, aiResult);
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(emailFrom);
            String[] recipients = parseRecipients(emailTo);
            helper.setTo(recipients);
            String subject = "Visao Cedente Identificada - " + analysis.getCompanyName();
            helper.setSubject(subject);
            helper.setText(buildPlainTextBody(analysis, aiResult), buildHtmlBody(analysis, aiResult));

            if (pdfBytes.length > 0) {
                helper.addAttachment(fileName, new ByteArrayDataSource(pdfBytes, "application/pdf"));
            }

            log.info("Enviando e-mail de Visão Cedente: from={} to={} subject='{}' attachment='{}' size={}B cnpj={}",
                    emailFrom, Arrays.toString(recipients), subject, fileName, pdfBytes.length, analysis.getCnpj());
            mailSender.send(message);
            log.info("E-mail de Visão Cedente enviado para {} — CNPJ={}", Arrays.toString(recipients), analysis.getCnpj());

        } catch (MessagingException | MailException e) {
            log.error("Falha ao enviar e-mail de Visão Cedente para CNPJ={}", analysis.getCnpj(), e);
            throw new IllegalStateException("Falha ao enviar e-mail via SMTP.", e);
        }
    }

    private String buildPdfFileName(CreditAnalysis analysis) {
        String baseName = analysis.getCompanyName() != null && !analysis.getCompanyName().isBlank()
                ? analysis.getCompanyName()
                : analysis.getCnpj();
        String sanitized = baseName.replaceAll("[\\\\/:*?\"<>|]", "").trim().replaceAll("\\s+", " ");
        return sanitized + ".pdf";
    }

    private GeminiAnalysisResult parseAiAnalysis(String aiAnalysisJson) {
        if (aiAnalysisJson == null || aiAnalysisJson.isBlank()) return null;
        try {
            return objectMapper.readValue(aiAnalysisJson, GeminiAnalysisResult.class);
        } catch (Exception e) {
            log.warn("Não foi possível parsear aiAnalysis: {}", e.getMessage());
            return null;
        }
    }

    private String buildHtmlBody(CreditAnalysis analysis, GeminiAnalysisResult ai) {
        String consultaEm = analysis.getConsultaEm() != null ? analysis.getConsultaEm().format(DATA_BR) : "—";
        String cnpjFormatado = formatarCnpj(analysis.getCnpj());
        String companyName = escapeHtml(analysis.getCompanyName() != null ? analysis.getCompanyName() : "—");
        String enderecoHtml = buildEnderecoHtml(analysis.getCreditRatingDetails());
        String telefoneHtml = buildTelefoneHtml(analysis.getCreditRatingDetails());
        String aiSection = ai != null ? buildAiSection(ai) : buildNoAiSection();

        return """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0"></head>
                <body style="margin:0;padding:0;background:#EBEBE6;font-family:'Helvetica Neue',Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#EBEBE6;padding:32px 16px;">
                    <tr><td align="center">
                      <table width="600" cellpadding="0" cellspacing="0" style="background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,.08);">

                        <!-- Header -->
                        <tr>
                          <td style="background:linear-gradient(135deg,#612035 0%%,#8a2d4a 100%%);padding:28px 32px;">
                            <p style="margin:0;font-size:11px;font-weight:700;color:rgba(255,255,255,.6);text-transform:uppercase;letter-spacing:.1em;">JGM Fomento · Portal Serasa</p>
                            <h1 style="margin:8px 0 0;font-size:22px;font-weight:700;color:#fff;line-height:1.3;">Empresa com Visão Cedente Identificada</h1>
                          </td>
                        </tr>

                        <!-- Company Info -->
                        <tr>
                          <td style="padding:24px 32px 0;">
                            <table width="100%%" cellpadding="0" cellspacing="0" style="border-radius:12px;overflow:hidden;border:1px solid #e5e7eb;">
                              <tr style="background:#f9fafb;">
                                <td style="padding:12px 16px;width:50%%;border-right:1px solid #e5e7eb;border-bottom:1px solid #e5e7eb;">
                                  <p style="margin:0 0 3px;font-size:10px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:.05em;">Razão Social</p>
                                  <p style="margin:0;font-size:15px;font-weight:700;color:#111;">%s</p>
                                </td>
                                <td style="padding:12px 16px;border-bottom:1px solid #e5e7eb;">
                                  <p style="margin:0 0 3px;font-size:10px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:.05em;">CNPJ</p>
                                  <p style="margin:0;font-size:15px;font-weight:700;color:#111;font-family:monospace;">%s</p>
                                </td>
                              </tr>
                              <tr>
                                <td colspan="2" style="padding:12px 16px;">
                                  <p style="margin:0 0 3px;font-size:10px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:.05em;">Data da Consulta</p>
                                  <p style="margin:0;font-size:13px;color:#374151;">%s</p>
                                </td>
                              </tr>
                              <tr>
                                <td style="padding:12px 16px;width:50%%;border-top:1px solid #e5e7eb;border-right:1px solid #e5e7eb;vertical-align:top;">
                                  <p style="margin:0 0 6px;font-size:10px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:.05em;">Endereço</p>
                                  <p style="margin:0;font-size:13px;color:#374151;line-height:1.65;">%s</p>
                                </td>
                                <td style="padding:12px 16px;border-top:1px solid #e5e7eb;vertical-align:top;">
                                  <p style="margin:0 0 6px;font-size:10px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:.05em;">Telefone</p>
                                  <p style="margin:0;font-size:13px;color:#374151;line-height:1.65;">%s</p>
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>

                        <!-- AI Analysis -->
                        %s

                        <!-- Footer -->
                        <tr>
                          <td style="padding:24px 32px 24px;border-top:1px solid #f3f4f6;background:#fafafa;">
                            <p style="margin:0;font-size:11px;color:#9ca3af;line-height:1.5;">
                              Esta notificação foi gerada automaticamente pelo Portal Serasa JGM Fomento.<br>
                              O relatório completo está anexado a este e-mail em formato PDF.
                            </p>
                          </td>
                        </tr>

                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(
                companyName, cnpjFormatado, consultaEm, enderecoHtml, telefoneHtml, aiSection
        );
    }

    private String buildPlainTextBody(CreditAnalysis analysis, GeminiAnalysisResult ai) {
        String consultaEm = analysis.getConsultaEm() != null ? analysis.getConsultaEm().format(DATA_BR) : "—";
        String cnpjFormatado = formatarCnpj(analysis.getCnpj());
        String companyName = analysis.getCompanyName() != null ? analysis.getCompanyName() : "—";
        String endereco = buildEnderecoText(analysis.getCreditRatingDetails());
        String telefone = buildTelefoneText(analysis.getCreditRatingDetails());
        String aiResumo = (ai != null && ai.isAvailable() && ai.getParecer() != null && !ai.getParecer().isBlank())
                ? ai.getParecer()
                : "Analise de IA nao disponivel no momento.";

        return """
                JGM Fomento - Portal Serasa

                Empresa com Visao Cedente Identificada

                Analise de Credito com IA:
                %s

                Razao Social: %s
                CNPJ: %s
                Data da Consulta: %s
                Endereco: %s
                Telefone: %s

                O relatorio completo segue em anexo em PDF.
                """.formatted(aiResumo, companyName, cnpjFormatado, consultaEm, endereco, telefone);
    }

    private String buildNegativeSection(JsonNode neg, JsonNode inq) {
        if (neg == null) return "";

        int pefin = count(neg, "pefin");
        int refin = count(neg, "refin");
        int cheques = count(neg, "check");
        int protestos = count(neg, "notary");
        int cobrancas = count(neg, "collectionRecords");
        double pefinVal = balance(neg, "pefin");
        double refinVal = balance(neg, "refin");
        double protestoVal = balance(neg, "notary");
        int falencias = inq != null ? inq.path("bankrupts").path("summary").path("count").asInt(0) : 0;
        int acoes = inq != null ? inq.path("judgementFilings").path("summary").path("count").asInt(0) : 0;

        return """
                <tr>
                  <td style="padding:20px 32px 0;">
                    <p style="margin:0 0 12px;font-size:12px;font-weight:700;color:#612035;text-transform:uppercase;letter-spacing:.05em;border-bottom:2px solid #612035;padding-bottom:6px;">
                      Resumo de Negativações
                    </p>
                    <table width="100%%" cellpadding="0" cellspacing="0" style="border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;">
                      <tr style="background:#612035;">
                        <td style="padding:8px 12px;font-size:10px;font-weight:700;color:#fff;text-transform:uppercase;">Tipo</td>
                        <td style="padding:8px 12px;font-size:10px;font-weight:700;color:#fff;text-transform:uppercase;text-align:center;">Qtd</td>
                        <td style="padding:8px 12px;font-size:10px;font-weight:700;color:#fff;text-transform:uppercase;text-align:right;">Valor</td>
                      </tr>
                      %s
                      %s
                      %s
                      %s
                      %s
                      %s
                      %s
                    </table>
                  </td>
                </tr>
                """.formatted(
                negRow("PEFIN", pefin, pefinVal),
                negRow("REFIN", refin, refinVal),
                negRow("Cheques sem Fundo", cheques, 0),
                negRow("Protestos", protestos, protestoVal),
                negRow("Cobranças", cobrancas, 0),
                negRow("Falências/Concordatas", falencias, 0),
                negRow("Ações Judiciais", acoes, 0)
        );
    }

    private String negRow(String label, int count, double value) {
        String countColor = count > 0 ? "#dc2626" : "#374151";
        String countStr = String.valueOf(count);
        String valStr = (value > 0) ? formatarMoeda(value) : (count > 0 ? "—" : "Nada Consta");
        String bg = count > 0 ? "background:#fef2f2;" : "";
        return """
                <tr style="%s">
                  <td style="padding:8px 12px;font-size:13px;color:#374151;border-bottom:1px solid #f3f4f6;">%s</td>
                  <td style="padding:8px 12px;font-size:13px;font-weight:700;color:%s;text-align:center;border-bottom:1px solid #f3f4f6;">%s</td>
                  <td style="padding:8px 12px;font-size:12px;color:%s;text-align:right;border-bottom:1px solid #f3f4f6;">%s</td>
                </tr>
                """.formatted(bg, label, countColor, countStr, countColor, valStr);
    }

    private String buildAiSection(GeminiAnalysisResult ai) {
        String riscoBg = getRiscoColor(ai.getNivelRisco());
        String riscoLabel = formatRisco(ai.getNivelRisco());
        String recLabel = formatRec(ai.getRecomendacao());
        String recBg = getRecColor(ai.getRecomendacao());

        String parecer = (ai.getParecer() != null && !ai.getParecer().isBlank())
                ? """
                  <div style="background:#ffffff;border:1px solid #e5e7eb;border-radius:14px;padding:18px 20px;margin-bottom:14px;">
                    <p style="margin:0 0 8px;font-size:11px;font-weight:700;color:#6b7280;text-transform:uppercase;letter-spacing:.08em;">
                      Parecer Executivo
                    </p>
                    <p style="margin:0;font-size:15px;color:#1f2937;line-height:1.75;">
                      %s
                    </p>
                  </div>
                  """.formatted(escapeHtml(ai.getParecer()))
                : "";

        String visaoCedenteAi = (ai.getVisaoCedente() != null && !ai.getVisaoCedente().isBlank())
                ? """
                  <div style="background:#f8fafc;border:1px solid #dbeafe;border-radius:14px;padding:16px 18px;margin-bottom:14px;">
                    <p style="margin:0 0 6px;font-size:11px;font-weight:700;color:#475569;text-transform:uppercase;letter-spacing:.08em;">Contexto de Visão Cedente</p>
                    <p style="margin:0;font-size:14px;color:#334155;line-height:1.7;">%s</p>
                  </div>
                  """.formatted(escapeHtml(ai.getVisaoCedente()))
                : "";

        String pontosFortes = buildPontos(ai.getPontosFortes(), "Pontos Positivos", "#16a34a", "#f0fdf4", "#dcfce7");
        String pontosAtencao = buildPontos(ai.getPontosAtencao(), "Pontos de Atenção", "#d97706", "#fffbeb", "#fef3c7");

        return """
                <tr>
                  <td style="padding:24px 32px 0;">
                    <div style="background:linear-gradient(180deg,#f8f4f6 0%%,#ffffff 100%%);border:1px solid #ead7df;border-radius:18px;padding:22px 22px 18px;">
                      <div style="display:flex;align-items:center;gap:8px;margin-bottom:16px;">
                        <div style="width:10px;height:10px;border-radius:999px;background:#612035;"></div>
                        <p style="margin:0;font-size:12px;font-weight:700;color:#612035;text-transform:uppercase;letter-spacing:.08em;">
                          Análise de Crédito com IA (Gemini)
                        </p>
                      </div>

                      <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:14px;">
                        <tr>
                          <td style="width:50%%;padding-right:6px;vertical-align:top;">
                            <div style="background:#ffffff;border:1px solid #e5e7eb;border-radius:14px;padding:14px 16px;">
                              <p style="margin:0 0 6px;font-size:10px;font-weight:700;color:#6b7280;text-transform:uppercase;letter-spacing:.08em;">Nível de Risco</p>
                              <p style="margin:0;font-size:16px;font-weight:700;color:%s;">%s</p>
                            </div>
                          </td>
                          <td style="width:50%%;padding-left:6px;vertical-align:top;">
                            <div style="background:#ffffff;border:1px solid #e5e7eb;border-radius:14px;padding:14px 16px;">
                              <p style="margin:0 0 6px;font-size:10px;font-weight:700;color:#6b7280;text-transform:uppercase;letter-spacing:.08em;">Recomendação</p>
                              <p style="margin:0;font-size:16px;font-weight:700;color:%s;">%s</p>
                            </div>
                          </td>
                        </tr>
                      </table>

                      %s
                      %s
                      %s
                      %s

                      <div style="margin-top:14px;padding-top:14px;border-top:1px solid #ead7df;">
                        <p style="margin:0;font-size:11px;color:#7c5b68;line-height:1.6;">
                          Esta leitura foi gerada por IA com base nos dados disponíveis no momento da consulta e deve ser usada como apoio à análise comercial.
                        </p>
                      </div>
                    </div>
                  </td>
                </tr>
                """.formatted(riscoBg, riscoLabel, recBg, recLabel, parecer, visaoCedenteAi, pontosFortes, pontosAtencao);
    }

    private String buildNoAiSection() {
        return """
                <tr>
                  <td style="padding:24px 32px 0;">
                    <div style="background:#f8fafc;border:1px solid #dbeafe;border-radius:14px;padding:18px 20px;">
                      <p style="margin:0 0 6px;font-size:11px;font-weight:700;color:#475569;text-transform:uppercase;letter-spacing:.08em;">
                        Análise de Crédito com IA
                      </p>
                      <p style="margin:0;font-size:14px;color:#334155;line-height:1.7;">
                        A análise de IA não foi gerada para esta empresa. Acesse o portal para gerar a análise e reenviar o e-mail.
                      </p>
                    </div>
                  </td>
                </tr>
                """;
    }

    private String buildPontos(String[] pontos, String title, String color, String bg, String border) {
        if (pontos == null || pontos.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"background:").append(bg).append(";border:1px solid ").append(border)
          .append(";border-radius:14px;padding:16px 18px;margin-top:12px;\">");
        sb.append("<p style=\"margin:0 0 10px;font-size:11px;font-weight:700;color:").append(color)
          .append(";text-transform:uppercase;letter-spacing:.08em;\">").append(title).append("</p>");
        for (String p : pontos) {
            sb.append("<p style=\"margin:0 0 8px;font-size:14px;color:#374151;line-height:1.65;\">")
              .append("<span style=\"color:").append(color).append(";font-weight:700;\">•</span> ")
              .append(escapeHtml(p))
              .append("</p>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private String getRiscoColor(String risco) {
        if (risco == null) return "#6b7280";
        return switch (risco) {
            case "BAIXO" -> "#16a34a";
            case "MODERADO" -> "#d97706";
            case "ALTO" -> "#ea580c";
            case "MUITO_ALTO" -> "#dc2626";
            default -> "#6b7280";
        };
    }

    private String getRecColor(String rec) {
        if (rec == null) return "#6b7280";
        return switch (rec) {
            case "APROVADO" -> "#16a34a";
            case "CONDICIONAL" -> "#d97706";
            case "NEGADO" -> "#dc2626";
            default -> "#6b7280";
        };
    }

    private String formatRisco(String r) {
        if (r == null) return "—";
        return switch (r) {
            case "BAIXO" -> "Baixo Risco";
            case "MODERADO" -> "Risco Moderado";
            case "ALTO" -> "Alto Risco";
            case "MUITO_ALTO" -> "Muito Alto Risco";
            default -> r;
        };
    }

    private String formatRec(String r) {
        if (r == null) return "—";
        return switch (r) {
            case "APROVADO" -> "✓ Aprovado";
            case "CONDICIONAL" -> "◐ Condicional";
            case "NEGADO" -> "✗ Negado";
            default -> r;
        };
    }

    private String formatarMoeda(double value) {
        return String.format("R$ %,.2f", value).replace(",", "X").replace(".", ",").replace("X", ".");
    }

    private int count(JsonNode parent, String key) {
        if (parent == null) return 0;
        return parent.path(key).path("summary").path("count").asInt(0);
    }

    private double balance(JsonNode parent, String key) {
        if (parent == null) return 0;
        return parent.path(key).path("summary").path("balance").asDouble(0);
    }

    private String formatarCnpj(String cnpj) {
        if (cnpj == null || cnpj.length() < 14) return cnpj != null ? cnpj : "—";
        return cnpj.replaceAll("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
    }

    private String[] parseRecipients(String rawRecipients) {
        if (rawRecipients == null || rawRecipients.isBlank()) {
            throw new IllegalStateException("Nenhum destinatario configurado em EMAIL_COMERCIAL.");
        }

        String[] recipients = Arrays.stream(rawRecipients.split("[,;]"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toArray(String[]::new);

        if (recipients.length == 0) {
            throw new IllegalStateException("Nenhum destinatario valido configurado em EMAIL_COMERCIAL.");
        }

        return recipients;
    }

    private String buildEnderecoHtml(JsonNode creditRatingDetails) {
        return escapeHtml(buildEnderecoText(creditRatingDetails));
    }

    private String buildTelefoneHtml(JsonNode creditRatingDetails) {
        return escapeHtml(buildTelefoneText(creditRatingDetails));
    }

    private String buildEnderecoText(JsonNode creditRatingDetails) {
        if (creditRatingDetails == null || creditRatingDetails.isMissingNode()) {
            return "Nao informado";
        }

        JsonNode address = creditRatingDetails.path("address");
        if (address.isMissingNode() || address.isNull()) {
            return "Nao informado";
        }

        String addressLine = text(address, "addressLine");
        String district = text(address, "district");
        String city = text(address, "city");
        String state = text(address, "state");
        String zipCode = text(address, "zipCode");

        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, addressLine, "");
        appendIfPresent(sb, district, sb.length() == 0 ? "" : " | ");

        String cityState = joinNonBlank(" - ", city, state);
        appendIfPresent(sb, cityState, sb.length() == 0 ? "" : " | ");
        appendIfPresent(sb, zipCode, sb.length() == 0 ? "CEP " : " | CEP ");

        return sb.length() > 0 ? sb.toString() : "Nao informado";
    }

    private String buildTelefoneText(JsonNode creditRatingDetails) {
        if (creditRatingDetails == null || creditRatingDetails.isMissingNode()) {
            return "Nao informado";
        }

        JsonNode phone = creditRatingDetails.path("phone");
        if (phone.isMissingNode() || phone.isNull()) {
            return "Nao informado";
        }

        String areaCode = text(phone, "areaCode");
        String phoneNumber = text(phone, "phoneNumber");

        if (areaCode.isBlank() && phoneNumber.isBlank()) {
            return "Nao informado";
        }

        if (!areaCode.isBlank() && !phoneNumber.isBlank()) {
            return "(" + areaCode + ") " + phoneNumber;
        }

        return !phoneNumber.isBlank() ? phoneNumber : areaCode;
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }

        String value = node.path(field).asText("");
        return value == null ? "" : value.trim();
    }

    private void appendIfPresent(StringBuilder sb, String value, String prefix) {
        if (value != null && !value.isBlank()) {
            sb.append(prefix).append(value);
        }
    }

    private String joinNonBlank(String separator, String... values) {
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(separator);
                }
                sb.append(value);
            }
        }
        return sb.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
