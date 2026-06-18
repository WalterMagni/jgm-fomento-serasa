package com.portal.serasa.application.service.credit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.serasa.domain.model.CompanyDetail;
import com.portal.serasa.domain.model.CreditAnalysis;
import com.portal.serasa.domain.model.credit.DadosAnalise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Testes unitários para CreditScoreExtractor.
 * Foco em segurança contra NullPointerException ao navegar nos nós JSONB.
 */
class CreditScoreExtractorTest {

    private CreditScoreExtractor extractor;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        extractor = new CreditScoreExtractor();
        mapper = new ObjectMapper();
    }

    // =========================================================
    // TESTES DE SEGURANÇA CONTRA NPE (nós JSONB nulos/ausentes)
    // =========================================================

    @Test
    @DisplayName("extrair: não deve lançar NPE quando negativeSummary é null")
    void shouldNotThrowNpe_whenNegativeSummaryIsNull() {
        CreditAnalysis analysis = buildAnalysis(null, null, null);

        assertThatCode(() -> extractor.extrair(analysis, Optional.empty()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("extrair: deve retornar zero pendências quando negativeSummary é null")
    void shouldReturnZeroPendencias_whenNegativeSummaryIsNull() {
        CreditAnalysis analysis = buildAnalysis(null, null, null);

        DadosAnalise dados = extractor.extrair(analysis, Optional.empty());

        assertThat(dados.getTotalPendencias()).isEqualTo(0);
        assertThat(dados.getTotalValorDividas()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("extrair: não deve lançar NPE quando bloco 'protestos' está ausente do JSONB")
    void shouldNotThrowNpe_whenProtestosBlockIsMissing() throws Exception {
        JsonNode negativeSummary = mapper.readTree(
                "{\"pefin\":{\"count\":2,\"balance\":\"1500.00\"}}"  // sem 'protestos'
        );
        CreditAnalysis analysis = buildAnalysis(negativeSummary, null, null);

        assertThatCode(() -> extractor.extrair(analysis, Optional.empty()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("extrair: deve usar asInt(0) como fallback quando 'protestos' está ausente")
    void shouldFallbackToZero_whenProtestosBlockIsMissing() throws Exception {
        JsonNode negativeSummary = mapper.readTree(
                "{\"pefin\":{\"count\":2,\"balance\":\"1500.00\"}}"
        );
        CreditAnalysis analysis = buildAnalysis(negativeSummary, null, null);

        DadosAnalise dados = extractor.extrair(analysis, Optional.empty());

        assertThat(dados.getPefinCount()).isEqualTo(2);
        assertThat(dados.getPefinBalance()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(dados.getProtestoCount()).isEqualTo(0);
        assertThat(dados.getProtestoBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("extrair: não deve lançar NPE quando partnerDetails é null")
    void shouldNotThrowNpe_whenPartnerDetailsIsNull() {
        CreditAnalysis analysis = buildAnalysis(null, null, null);

        DadosAnalise dados = extractor.extrair(analysis, Optional.empty());

        assertThat(dados.getQuantidadeSocios()).isEqualTo(0);
        assertThat(dados.isPossuiSocioComRestricao()).isFalse();
    }

    @Test
    @DisplayName("extrair: não deve lançar NPE quando partnerDetails não é array")
    void shouldNotThrowNpe_whenPartnerDetailsIsNotArray() throws Exception {
        JsonNode partnerDetails = mapper.readTree("{}");  // objeto, não array
        CreditAnalysis analysis = buildAnalysis(null, partnerDetails, null);

        assertThatCode(() -> extractor.extrair(analysis, Optional.empty()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("extrair: não deve lançar NPE quando inquiryHistory é null")
    void shouldNotThrowNpe_whenInquiryHistoryIsNull() {
        CreditAnalysis analysis = buildAnalysis(null, null, null);

        DadosAnalise dados = extractor.extrair(analysis, Optional.empty());

        assertThat(dados.getConsultasTotal()).isEqualTo(0);
        assertThat(dados.getConsultasUltimos30Dias()).isEqualTo(0);
        assertThat(dados.getConsultasUltimos90Dias()).isEqualTo(0);
    }

    // =========================================================
    // TESTES DE LÓGICA DE EXTRAÇÃO COM DADOS VÁLIDOS
    // =========================================================

    @Test
    @DisplayName("extrair: deve calcular corretamente total pendências e valor com negativeSummary completo")
    void shouldCalculateTotalsProperly_withFullNegativeSummary() throws Exception {
        JsonNode negativeSummary = mapper.readTree("""
                {
                  "pefin":          {"count": 1, "balance": "500.00"},
                  "refin":          {"count": 2, "balance": "300.00"},
                  "protestos":      {"count": 1, "balance": "200.00"},
                  "acoesJudiciais": {"count": 0, "balance": "0"},
                  "cheques":        {"count": 1, "balance": "150.00"}
                }
                """);
        CreditAnalysis analysis = buildAnalysis(negativeSummary, null, null);

        DadosAnalise dados = extractor.extrair(analysis, Optional.empty());

        assertThat(dados.getTotalPendencias()).isEqualTo(5);
        assertThat(dados.getTotalValorDividas()).isEqualByComparingTo(new BigDecimal("1150.00"));
    }

    @Test
    @DisplayName("extrair: deve detectar sócio com restrição corretamente")
    void shouldDetectPartnerWithRestriction() throws Exception {
        JsonNode partnerDetails = mapper.readTree("""
                [
                  {"nome": "Sócio Limpo",  "possuiRestricao": false},
                  {"nome": "Sócio Ruim",   "possuiRestricao": true}
                ]
                """);
        CreditAnalysis analysis = buildAnalysis(null, partnerDetails, null);

        DadosAnalise dados = extractor.extrair(analysis, Optional.empty());

        assertThat(dados.isPossuiSocioComRestricao()).isTrue();
        assertThat(dados.getSocioComRestricaoNome()).isEqualTo("Sócio Ruim");
        assertThat(dados.getQuantidadeSocios()).isEqualTo(2);
    }

    @Test
    @DisplayName("extrair: deve calcular idade da empresa em meses quando CompanyDetail está presente")
    void shouldCalculateCompanyAgeInMonths_whenCompanyDetailIsPresent() {
        LocalDate founded = LocalDate.now().minusMonths(36);
        CompanyDetail company = CompanyDetail.builder()
                .founded(founded)
                .sizeText("MEDIO")
                .sizeAcronym("ME")
                .build();
        CreditAnalysis analysis = buildAnalysis(null, null, null);

        DadosAnalise dados = extractor.extrair(analysis, Optional.of(company));

        assertThat(dados.getIdadeMeses()).isBetween(35, 37);
        assertThat(dados.getPorte()).isEqualTo("MEDIO");
    }

    @Test
    @DisplayName("extrair: deve usar fallback 999 quando CompanyDetail está ausente")
    void shouldUseFallback999_whenCompanyDetailIsAbsent() {
        CreditAnalysis analysis = buildAnalysis(null, null, null);

        DadosAnalise dados = extractor.extrair(analysis, Optional.empty());

        assertThat(dados.getIdadeMeses()).isEqualTo(999);
    }

    @Test
    @DisplayName("extrair: deve contar consultas dos últimos 30 e 90 dias corretamente")
    void shouldCountInquiriesByPeriod() throws Exception {
        String hoje = LocalDate.now().toString();
        String ha20Dias = LocalDate.now().minusDays(20).toString();
        String ha60Dias = LocalDate.now().minusDays(60).toString();
        String ha120Dias = LocalDate.now().minusDays(120).toString();

        JsonNode inquiryHistory = mapper.readTree("""
                [
                  {"data": "%s"},
                  {"data": "%s"},
                  {"data": "%s"},
                  {"data": "%s"}
                ]
                """.formatted(hoje, ha20Dias, ha60Dias, ha120Dias));

        CreditAnalysis analysis = buildAnalysis(null, null, inquiryHistory);

        DadosAnalise dados = extractor.extrair(analysis, Optional.empty());

        assertThat(dados.getConsultasTotal()).isEqualTo(4);
        assertThat(dados.getConsultasUltimos30Dias()).isEqualTo(2);
        assertThat(dados.getConsultasUltimos90Dias()).isEqualTo(3);
    }

    @Test
    @DisplayName("extrair: deve ignorar datas malformadas em inquiryHistory sem lançar exceção")
    void shouldIgnoreMalformedDates_inInquiryHistory() throws Exception {
        JsonNode inquiryHistory = mapper.readTree("""
                [
                  {"data": "invalid-date"},
                  {"data": ""},
                  {"data": null}
                ]
                """);
        CreditAnalysis analysis = buildAnalysis(null, null, inquiryHistory);

        assertThatCode(() -> extractor.extrair(analysis, Optional.empty()))
                .doesNotThrowAnyException();

        DadosAnalise dados = extractor.extrair(analysis, Optional.empty());
        assertThat(dados.getConsultasTotal()).isEqualTo(3);
        assertThat(dados.getConsultasUltimos30Dias()).isEqualTo(0);
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private CreditAnalysis buildAnalysis(JsonNode negativeSummary, JsonNode partnerDetails, JsonNode inquiryHistory) {
        return CreditAnalysis.builder()
                .cnpj("12345678000195")
                .score(500)
                .riskClass("B")
                .negativeSummary(negativeSummary)
                .partnerDetails(partnerDetails)
                .inquiryHistory(inquiryHistory)
                .build();
    }
}
