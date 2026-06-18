package com.portal.serasa.application.service.credit;

import com.fasterxml.jackson.databind.JsonNode;
import com.portal.serasa.domain.model.CompanyDetail;
import com.portal.serasa.domain.model.CreditAnalysis;
import com.portal.serasa.domain.model.credit.DadosAnalise;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Extrator atualizado para ler da nova estrutura híbrida (CreditAnalysis +
 * CompanyDetail).
 */
@Slf4j
@Component
public class CreditScoreExtractor {

    public DadosAnalise extrair(CreditAnalysis analysis, Optional<CompanyDetail> companyOpt) {
        log.debug("Extraindo dados de análise para CNPJ: {}", analysis.getCnpj());

        DadosAnalise.DadosAnaliseBuilder builder = DadosAnalise.builder();

        // 1. Dados Relacionais do CreditAnalysis
        builder.scoreHPJ8(analysis.getScore());
        builder.classeRisco(analysis.getRiskClass());
        if (analysis.getProbability() != null) {
            builder.probabilidadeInadimplencia(analysis.getProbability().doubleValue());
        }

        // 2. Extrair das colunas JSONB
        extrairDadosNegativos(analysis.getNegativeSummary(), builder);
        extrairSocios(analysis.getPartnerDetails(), builder);
        extrairConsultas(analysis.getInquiryHistory(), builder);

        // 3. Extrair dados do CompanyDetail (se disponível)
        if (companyOpt.isPresent()) {
            CompanyDetail company = companyOpt.get();
            calcularIdade(company.getFounded(), builder);
            builder.porte(company.getSizeText());
            builder.porteCodigo(company.getSizeAcronym());
        } else {
            builder.idadeMeses(999); // Fallback caso não haja dados da empresa
        }

        DadosAnalise dados = builder.build();

        log.debug("Dados extraídos - Score: {}, Pendências: {}, Dívidas R$: {}",
                dados.getScoreHPJ8(), dados.getTotalPendencias(), dados.getTotalValorDividas());

        return dados;
    }

    private void extrairDadosNegativos(JsonNode node, DadosAnalise.DadosAnaliseBuilder builder) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            builder.totalPendencias(0).totalValorDividas(BigDecimal.ZERO);
            return;
        }

        // Exemplo genérico de como o Serasa poderia retornar no JSON original parseado
        // Adaptável conforme a estrutura real do seu JsonNode
        int pefinCount = node.path("pefin").path("count").asInt(0);
        BigDecimal pefinBalance = getDecimal(node.path("pefin").path("balance"));

        int refinCount = node.path("refin").path("count").asInt(0);
        BigDecimal refinBalance = getDecimal(node.path("refin").path("balance"));

        int protestoCount = node.path("protestos").path("count").asInt(0);
        BigDecimal protestoBalance = getDecimal(node.path("protestos").path("balance"));

        int acaoJudicialCount = node.path("acoesJudiciais").path("count").asInt(0);
        BigDecimal acaoJudicialBalance = getDecimal(node.path("acoesJudiciais").path("balance"));

        int chequeCount = node.path("cheques").path("count").asInt(0);
        BigDecimal chequeBalance = getDecimal(node.path("cheques").path("balance"));

        int totalCount = pefinCount + refinCount + protestoCount + acaoJudicialCount + chequeCount;
        BigDecimal totalValor = pefinBalance.add(refinBalance).add(protestoBalance)
                .add(acaoJudicialBalance).add(chequeBalance);

        builder.pefinCount(pefinCount).pefinBalance(pefinBalance)
                .refinCount(refinCount).refinBalance(refinBalance)
                .protestoCount(protestoCount).protestoBalance(protestoBalance)
                .acaoJudicialCount(acaoJudicialCount).acaoJudicialBalance(acaoJudicialBalance)
                .chequeCount(chequeCount).chequeBalance(chequeBalance)
                .totalPendencias(totalCount).totalValorDividas(totalValor);
    }

    private void extrairSocios(JsonNode node, DadosAnalise.DadosAnaliseBuilder builder) {
        if (node == null || !node.isArray()) {
            builder.quantidadeSocios(0).possuiSocioComRestricao(false);
            return;
        }

        builder.quantidadeSocios(node.size());
        boolean hasRestricao = false;
        String socioRestricaoNome = null;

        for (JsonNode socio : node) {
            boolean restrito = socio.path("possuiRestricao").asBoolean(false);
            if (restrito && !hasRestricao) {
                hasRestricao = true;
                socioRestricaoNome = socio.path("nome").asText(null);
            }
        }

        builder.possuiSocioComRestricao(hasRestricao)
                .socioComRestricaoNome(socioRestricaoNome);
    }

    private void extrairConsultas(JsonNode node, DadosAnalise.DadosAnaliseBuilder builder) {
        if (node == null || !node.isArray()) {
            builder.consultasUltimos30Dias(0)
                    .consultasUltimos90Dias(0)
                    .consultasTotal(0);
            return;
        }

        int total = node.size();
        int qtd30 = 0;
        int qtd90 = 0;

        LocalDate hoje = LocalDate.now();
        LocalDate limit30 = hoje.minusDays(30);
        LocalDate limit90 = hoje.minusDays(90);

        for (JsonNode consulta : node) {
            String dateStr = consulta.path("data").asText();
            if (dateStr != null && !dateStr.isBlank()) {
                try {
                    // Tenta o formato ISO ou yyyy-MM-dd
                    LocalDate data = LocalDate.parse(dateStr.substring(0, 10));
                    if (data.isAfter(limit30))
                        qtd30++;
                    if (data.isAfter(limit90))
                        qtd90++;
                } catch (Exception e) {
                    log.trace("Erro ao fazer parse de data de consulta: {}", dateStr);
                }
            }
        }

        builder.consultasTotal(total)
                .consultasUltimos30Dias(qtd30)
                .consultasUltimos90Dias(qtd90);
    }

    private void calcularIdade(LocalDate founded, DadosAnalise.DadosAnaliseBuilder builder) {
        builder.dataFundacao(founded);
        if (founded != null) {
            long meses = ChronoUnit.MONTHS.between(founded, LocalDate.now());
            builder.idadeMeses((int) meses);
        } else {
            builder.idadeMeses(999);
        }
    }

    private BigDecimal getDecimal(JsonNode node) {
        if (node != null && !node.isMissingNode() && !node.isNull()) {
            return new BigDecimal(node.asText("0").replaceAll("[^0-9.]", ""));
        }
        return BigDecimal.ZERO;
    }
}
