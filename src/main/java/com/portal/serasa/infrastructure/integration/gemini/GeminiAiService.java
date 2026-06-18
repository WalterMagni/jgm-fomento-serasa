package com.portal.serasa.infrastructure.integration.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.serasa.domain.model.CreditAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Envia dados do Serasa ao Gemini e retorna análise de crédito
 * focada na visão cedente de factoring.
 *
 * Dados pessoais sensíveis (CPF individual) são omitidos para conformidade LGPD.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GeminiAiService {

    private static final String GEMINI_URL_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash-lite}")
    private String model;

    private final ObjectMapper objectMapper;

    public GeminiAnalysisResult analyze(CreditAnalysis ca) {
        if (apiKey == null || apiKey.isBlank()) {
            return GeminiAnalysisResult.unavailable("Chave Gemini não configurada.");
        }

        String prompt = buildPrompt(ca);
        String rawResponse = callGemini(prompt);
        return parseResponse(rawResponse);
    }

    /**
     * Justificativa em linguagem natural para a análise de praça de pagamento.
     * O score determinístico continua sendo a base; o Gemini explica e dá uma
     * segunda opinião, sem ser o decisor final.
     */
    public PaymentPlaceAiResult analyzePaymentPlace(String context) {
        if (apiKey == null || apiKey.isBlank()) {
            return PaymentPlaceAiResult.unavailable("Chave Gemini não configurada.");
        }

        String prompt = """
                Você é analista de uma factoring avaliando a PRAÇA DE PAGAMENTO de um título de retorno bancário.
                Decida se o pagamento provavelmente partiu do SACADO ou do CEDENTE, ou se é INCONCLUSIVO.
                A praça bancária é um SINAL, não prova. Use SOMENTE os dados fornecidos; não invente nada.
                Responda SOMENTE com JSON válido, sem markdown, em português do Brasil.

                Dados do lançamento:
                %s

                JSON de saída:
                {"suggestion":"PROVAVEL_SACADO|PROVAVEL_CEDENTE|INCONCLUSIVO","confidence":"ALTA|MEDIA|BAIXA","summary":"2 a 3 frases explicando a conclusão","factors_for":["fatores que apoiam a sugestão"],"factors_against":["fatores contra / incertezas"],"recommendation":"recomendação curta ao analista"}
                """.formatted(context);

        return parsePaymentPlaceResponse(callGemini(prompt));
    }

    private PaymentPlaceAiResult parsePaymentPlaceResponse(String rawResponse) {
        if (rawResponse == null) {
            return PaymentPlaceAiResult.unavailable("Não foi possível conectar ao Gemini.");
        }
        if (rawResponse.equals("HTTP_429")) {
            return PaymentPlaceAiResult.unavailable("Limite de requisições do Gemini atingido. Tente novamente em alguns segundos.");
        }
        if (rawResponse.startsWith("HTTP_")) {
            return PaymentPlaceAiResult.unavailable("Gemini retornou erro " + rawResponse.replace("HTTP_", "") + ".");
        }
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
            text = text.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();
            JsonNode a = objectMapper.readTree(text);
            return PaymentPlaceAiResult.builder()
                    .available(true)
                    .suggestion(a.path("suggestion").asText("INCONCLUSIVO"))
                    .confidence(a.path("confidence").asText("BAIXA"))
                    .summary(a.path("summary").asText(""))
                    .factorsFor(java.util.Arrays.asList(toStringArray(a.path("factors_for"))))
                    .factorsAgainst(java.util.Arrays.asList(toStringArray(a.path("factors_against"))))
                    .recommendation(a.path("recommendation").asText(""))
                    .build();
        } catch (Exception e) {
            log.error("Erro ao parsear resposta do Gemini (praça): {}", e.getMessage());
            return PaymentPlaceAiResult.unavailable("Erro ao processar resposta da IA.");
        }
    }

    public GeminiPortfolioReportResult generateVisaoCedenteReport(String userPrompt, String context) {
        if (apiKey == null || apiKey.isBlank()) {
            return GeminiPortfolioReportResult.unavailable("Chave Gemini não configurada.");
        }

        String prompt = """
                Você é um analista comercial senior de factoring/fomento.
                Use SOMENTE o contexto fornecido e responda SOMENTE com JSON válido, sem markdown.

                Objetivo do usuário:
                %s

                Contexto consolidado do BI Visão Cedente:
                %s

                Regras:
                - não invente empresas nem números
                - escreva em português do Brasil
                - destaque critérios, padrões e oportunidades comerciais
                - se o recorte vier vazio, explique isso com objetividade

                JSON:
                {"title":"titulo curto","summary":"2 a 4 frases executivas","highlights":["...","...","..."],"recommendations":["...","...","..."]}
                """.formatted(userPrompt, context);

        String rawResponse = callGemini(prompt);
        return parsePortfolioReportResponse(rawResponse);
    }

    // ── Prompt (compacto para respeitar rate limits do plano gratuito) ──────────

    private String buildPrompt(CreditAnalysis ca) {
        // Coleta apenas os números essenciais — sem texto narrativo longo
        JsonNode neg = ca.getNegativeSummary();
        JsonNode inq = ca.getInquiryHistory();
        JsonNode qsa = ca.getPartnerDetails();
        JsonNode id  = ca.getCreditRatingDetails();

        int pefin      = count(neg, "pefin");
        int refin      = count(neg, "refin");
        int cheques    = count(neg, "check");
        int protestos  = count(neg, "notary");
        int cobrancas  = count(neg, "collectionRecords");
        int falencias  = count(inq, "bankrupts");
        int acoes      = count(inq, "judgementFilings");

        double pefinVal    = balance(neg, "pefin");
        double refinVal    = balance(neg, "refin");
        double protestoVal = balance(neg, "notary");
        String negativeMessage = fieldText(neg, "message");
        boolean hasNadaConstaSignal = "NADA CONSTA".equalsIgnoreCase(negativeMessage);

        int socios = 0, sociosRestritos = 0;
        if (qsa != null) {
            JsonNode lista = qsa.path("partnerCompleteReport").path("partnersList");
            if (lista.isArray()) {
                socios = lista.size();
                for (JsonNode p : lista) if (p.path("restrictionSign").asBoolean()) sociosRestritos++;
            }
        }

        String situacao  = fieldText(id, "statusCodeDescription");
        String atividade = fieldText(id, "economicActivity");
        String regime    = fieldText(id, "taxOption");
        int funcionarios = (id != null) ? id.path("numberEmployees").asInt(0) : 0;

        String prompt = String.format("""
                Analista de crédito factoring. Responda SOMENTE com JSON válido, sem markdown.
                Empresa: %s | Situação: %s | Atividade: %s | Regime: %s | Funcionários: %d
                Negativações: PEFIN=%d(R$%.0f) REFIN=%d(R$%.0f) Cheques=%d Protestos=%d(R$%.0f) Cobranças=%d
                Ocorrências: Falências=%d AçõesJudiciais=%d
                QSA: %d sócios, %d com restrição Serasa
                Sinalização textual Serasa em negativações: %s
                Regra importante:
                - se houver a sinalização textual "NADA CONSTA", trate isso como ALERTA e ponto de atenção
                - não assuma que significa ausência de risco
                - não afirme que existe liminar ou bloqueio judicial, mas registre que pode haver restrição de exibição de ocorrências
                - essa sinalização deve pesar negativamente na análise e na recomendação final
                JSON: {"parecer":"2 parágrafos","visao_cedente":"1 parágrafo sobre risco cedente factoring","nivel_risco":"BAIXO|MODERADO|ALTO|MUITO_ALTO","recomendacao":"APROVADO|CONDICIONAL|NEGADO","pontos_fortes":["..."],"pontos_atencao":["..."]}
                """,
                safe(ca.getCompanyName()), situacao, atividade, regime, funcionarios,
                pefin, pefinVal, refin, refinVal, cheques, protestos, protestoVal, cobrancas,
                falencias, acoes, socios, sociosRestritos,
                hasNadaConstaSignal ? "NADA CONSTA (tratar como alerta)" : negativeMessage);

        return prompt;
    }

    private int count(JsonNode parent, String key) {
        if (parent == null) return 0;
        return parent.path(key).path("summary").path("count").asInt(0);
    }

    private double balance(JsonNode parent, String key) {
        if (parent == null) return 0;
        return parent.path(key).path("summary").path("balance").asDouble(0);
    }

    private String fieldText(JsonNode node, String key) {
        if (node == null) return "-";
        String v = node.path(key).asText("");
        return v.isBlank() ? "-" : v;
    }

    private void appendField(StringBuilder sb, JsonNode node, String key, String label) {
        JsonNode val = node.path(key);
        if (!val.isMissingNode() && !val.isNull() && !val.asText().isBlank() && !val.asText().equals("null")) {
            sb.append(label).append(": ").append(val.asText()).append("\n");
        }
    }

    private String safe(String value) {
        return value != null ? value : "-";
    }

    // ── Gemini HTTP call ──────────────────────────────────────────────────────

    private String callGemini(String prompt) {
        try {
            String jsonBody = objectMapper.writeValueAsString(java.util.Map.of(
                "contents", java.util.List.of(
                    java.util.Map.of("parts", java.util.List.of(
                        java.util.Map.of("text", prompt)
                    ))
                ),
                "generationConfig", java.util.Map.of(
                    "temperature", 0.3,
                    "maxOutputTokens", 4096
                )
            ));

            String modelName = (model == null || model.isBlank()) ? "gemini-2.5-flash-lite" : model.trim();
            java.net.URL url = new java.net.URL(GEMINI_URL_TEMPLATE.formatted(modelName) + "?key=" + apiKey);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(30_000);
            conn.setDoOutput(true);

            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            java.io.InputStream is = (status < 400) ? conn.getInputStream() : conn.getErrorStream();
            String response = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

            if (status == 429) {
                log.warn("Gemini rate limit atingido (429)");
                return "HTTP_429";
            }
            if (status != 200) {
                log.error("Gemini retornou HTTP {}: {}", status, response);
                return "HTTP_" + status;
            }
            return response;
        } catch (Exception e) {
            log.error("Erro ao chamar Gemini API: {}", e.getMessage(), e);
            return null;
        }
    }

    // ── Response parsing ──────────────────────────────────────────────────────

    private GeminiAnalysisResult parseResponse(String rawResponse) {
        if (rawResponse == null) {
            return GeminiAnalysisResult.unavailable("Não foi possível conectar ao Gemini. Verifique a conectividade.");
        }
        if (rawResponse.equals("HTTP_429")) {
            return GeminiAnalysisResult.unavailable("Limite de requisições do Gemini atingido. Aguarde alguns segundos e clique em Reanalisar.");
        }
        if (rawResponse.startsWith("HTTP_")) {
            return GeminiAnalysisResult.unavailable("Gemini retornou erro " + rawResponse.replace("HTTP_", "") + ". Verifique a chave de API.");
        }
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            String text = root.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText("");

            // Remove possível markdown code block
            text = text.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();

            JsonNode analysis = objectMapper.readTree(text);

            return GeminiAnalysisResult.builder()
                    .available(true)
                    .parecer(analysis.path("parecer").asText(""))
                    .visaoCedente(analysis.path("visao_cedente").asText(""))
                    .nivelRisco(analysis.path("nivel_risco").asText("MODERADO"))
                    .recomendacao(analysis.path("recomendacao").asText("CONDICIONAL"))
                    .pontosFortes(toStringArray(analysis.path("pontos_fortes")))
                    .pontosAtencao(toStringArray(analysis.path("pontos_atencao")))
                    .build();
        } catch (Exception e) {
            log.error("Erro ao parsear resposta do Gemini: {}", e.getMessage());
            return GeminiAnalysisResult.unavailable("Erro ao processar resposta da IA.");
        }
    }

    private GeminiPortfolioReportResult parsePortfolioReportResponse(String rawResponse) {
        if (rawResponse == null) {
            return GeminiPortfolioReportResult.unavailable("Não foi possível conectar ao Gemini. Verifique a conectividade.");
        }
        if (rawResponse.equals("HTTP_429")) {
            return GeminiPortfolioReportResult.unavailable("Limite de requisições do Gemini atingido. Aguarde alguns segundos e tente novamente.");
        }
        if (rawResponse.startsWith("HTTP_")) {
            return GeminiPortfolioReportResult.unavailable("Gemini retornou erro " + rawResponse.replace("HTTP_", "") + ". Verifique a chave de API.");
        }

        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            String text = root.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText("");

            text = text.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();

            JsonNode report = objectMapper.readTree(text);

            return GeminiPortfolioReportResult.builder()
                    .available(true)
                    .title(report.path("title").asText("Relatório IA"))
                    .summary(report.path("summary").asText(""))
                    .highlights(toStringArray(report.path("highlights")))
                    .recommendations(toStringArray(report.path("recommendations")))
                    .build();
        } catch (Exception e) {
            log.error("Erro ao parsear relatório de carteira do Gemini: {}", e.getMessage());
            return GeminiPortfolioReportResult.unavailable("Erro ao processar resposta da IA.");
        }
    }

    private String[] toStringArray(JsonNode arrayNode) {
        if (!arrayNode.isArray()) return new String[0];
        String[] result = new String[arrayNode.size()];
        for (int i = 0; i < arrayNode.size(); i++) {
            result[i] = arrayNode.get(i).asText();
        }
        return result;
    }
}
