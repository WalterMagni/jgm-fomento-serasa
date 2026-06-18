package com.portal.serasa.application.service.credit;

import com.portal.serasa.domain.model.CompanyDetail;
import com.portal.serasa.domain.model.CreditAnalysis;
import com.portal.serasa.domain.model.credit.DadosAnalise;
import com.portal.serasa.domain.model.credit.PerfilCredito;
import com.portal.serasa.domain.model.credit.ResultadoAnaliseCredito;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Motor de Política de Crédito atualizado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MotorCreditoService {

    private final CreditScoreExtractor extractor;

    private static final int SCORE_CRITICO = 200;
    private static final int SCORE_BAIXO = 400;
    private static final int SCORE_PRIME = 750;
    private static final int SCORE_JURIDICO_RISCO = 450;
    private static final int SCORE_JURIDICO_BOM = 800;
    private static final int PONTUALIDADE_RUIM = 50;
    private static final int PONTUALIDADE_BOA = 80;
    private static final int EMPRESA_NOVA_MESES = 24;
    private static final int CONSULTAS_EXCESSIVAS = 5;
    private static final double DIVIDA_RELEVANTE = 2000.0;
    private static final int FATURAMENTO_GRANDE = 80;

    public ResultadoAnaliseCredito analisar(CreditAnalysis analysis, Optional<CompanyDetail> companyOpt) {
        log.info("Iniciando análise de crédito HÍBRIDA para CNPJ: {}", analysis.getCnpj());

        DadosAnalise dados = extractor.extrair(analysis, companyOpt);
        PerfilCredito perfil = aplicarRegras(dados);
        ResultadoAnaliseCredito resultado = montarResultado(analysis, dados, perfil);
        adicionarInsights(resultado, dados);

        log.info("Análise concluída: CNPJ {} -> Perfil {} ({})",
                analysis.getCnpj(), perfil.name(), perfil.getDecisao());

        return resultado;
    }

    private PerfilCredito aplicarRegras(DadosAnalise d) {
        if (d.temScoreCritico() || d.temDividasRelevantes(DIVIDA_RELEVANTE))
            return PerfilCredito.REJEICAO;
        if (d.isPossuiSocioComRestricao())
            return PerfilCredito.RISCO_SOCIO;
        if (scoreMaiorQue(d.getScoreHPJ8(), 600) && scoreMenorQue(d.getScoreHSJ6(), SCORE_JURIDICO_RISCO))
            return PerfilCredito.RISCO_JURIDICO;
        if (d.getConsultasUltimos30Dias() > CONSULTAS_EXCESSIVAS && scoreMenorQue(d.getScoreHPJ8(), 700))
            return PerfilCredito.FOME_CREDITO;
        if (scoreMenorQue(d.getScoreHPJ8(), SCORE_BAIXO) || d.temNegativosAtivos())
            return PerfilCredito.RECUPERACAO;
        if (d.temPontualidadeRuim(PONTUALIDADE_RUIM))
            return PerfilCredito.ALERTA_OPERACIONAL;
        if (d.isEmpresaNova(EMPRESA_NOVA_MESES) && !d.temNegativosAtivos())
            return PerfilCredito.STARTUP;
        if (scoreMaiorQue(d.getScoreHFE3(), FATURAMENTO_GRANDE) && d.getTotalValorDividas() != null
                && d.getTotalDividasDouble() < DIVIDA_RELEVANTE && d.getTotalDividasDouble() > 0)
            return PerfilCredito.GIGANTE_BUROCRATICO;
        if (scoreMaiorQue(d.getScoreHPJ8(), SCORE_PRIME) && scoreMaiorQue(d.getScoreHSJ6(), SCORE_JURIDICO_BOM))
            return PerfilCredito.PRIME;

        return PerfilCredito.STANDARD;
    }

    private ResultadoAnaliseCredito montarResultado(CreditAnalysis f, DadosAnalise d, PerfilCredito perfil) {
        return ResultadoAnaliseCredito.builder()
                .cnpj(f.getCnpj())
                .razaoSocial(f.getCompanyName())
                .nomeFantasia(f.getCompanyName())
                .dataAnalise(LocalDateTime.now())
                .perfil(perfil)
                .decisao(perfil.getDecisao())
                .titulo(perfil.getTitulo())
                .corHex(perfil.getCorHex())
                .mensagem(gerarMensagemContextualizada(perfil, d))
                .scoreCredito(d.getScoreHPJ8())
                .scorePontualidade(d.getScoreHIP2())
                .scoreJuridico(d.getScoreHSJ6())
                .scoreFaturamento(d.getScoreHFE3())
                .scoreLongevidade(d.getScoreHLO4())
                .probabilidadeInadimplencia(d.getProbabilidadeInadimplencia())
                .probabilidadeEncerramento(d.getProbabilidadeEncerramento())
                .idadeEmpresaMeses(d.getIdadeMeses())
                .totalPendenciasFinanceiras(d.getTotalPendencias())
                .totalValorDividas(d.getTotalDividasDouble())
                .consultasRecentes(d.getConsultasUltimos30Dias())
                .limiteCredito(sugerirLimite(perfil))
                .prazoPagamento(sugerirPrazo(perfil))
                .build();
    }

    private String gerarMensagemContextualizada(PerfilCredito perfil, DadosAnalise d) {
        return switch (perfil) {
            case REJEICAO -> String.format(
                    "REPROVADO. Empresa possui %d restrições financeiras totalizando R$ %.2f e Score de Crédito %d. Alta probabilidade de insolvência.",
                    d.getTotalPendencias() != null ? d.getTotalPendencias() : 0, d.getTotalDividasDouble(),
                    d.getScoreHPJ8() != null ? d.getScoreHPJ8() : 0);
            case RISCO_SOCIO ->
                String.format("Empresa ativa, porém sócio%s possui restrições. Risco de confusão patrimonial.",
                        d.getSocioComRestricaoNome() != null ? " (" + d.getSocioComRestricaoNome() + ")" : "");
            case RISCO_JURIDICO -> String.format(
                    "Alerta: Score Crédito %d, mas Score Jurídico %d. Possível passivo oculto.",
                    d.getScoreHPJ8() != null ? d.getScoreHPJ8() : 0, d.getScoreHSJ6() != null ? d.getScoreHSJ6() : 0);
            case FOME_CREDITO ->
                String.format("Empresa buscou crédito %d vezes nos últimos 30 dias. Risco no curto prazo.",
                        d.getConsultasUltimos30Dias());
            case RECUPERACAO -> String.format("Histórico de dificuldades (Score %d). %s",
                    d.getScoreHPJ8() != null ? d.getScoreHPJ8() : 0,
                    d.temNegativosAtivos()
                            ? String.format("Possui %d pendências no valor de R$ %.2f.", d.getTotalPendencias(),
                                    d.getTotalDividasDouble())
                            : "");
            case ALERTA_OPERACIONAL -> String.format(
                    "Solvente (Score %d), mas Hábito de Pagamento Ruim (Pontualidade %d%%).",
                    d.getScoreHPJ8() != null ? d.getScoreHPJ8() : 0, d.getScoreHIP2() != null ? d.getScoreHIP2() : 0);
            case STARTUP -> String.format(
                    "Empresa nova (%d meses). Sem histórico negativo, score coerente para a idade.", d.getIdadeMeses());
            case GIGANTE_BUROCRATICO -> String.format(
                    "Grande porte com restrições imateriais (R$ %.2f). Ruído operacional.", d.getTotalDividasDouble());
            case PRIME -> String.format(
                    "Excelente pagador (Score de Crédito %d, Score Jurídico %d). Limite máximo sugerido.",
                    d.getScoreHPJ8() != null ? d.getScoreHPJ8() : 0, d.getScoreHSJ6() != null ? d.getScoreHSJ6() : 0);
            case STANDARD -> String.format("Estabilidade adequada (Score %d). Risco compatível com o porte.",
                    d.getScoreHPJ8() != null ? d.getScoreHPJ8() : 0);
        };
    }

    private void adicionarInsights(ResultadoAnaliseCredito r, DadosAnalise d) {
        if (d.getScoreHIP2() != null && d.getScoreHIP2() > PONTUALIDADE_BOA)
            r.addPontoPositivo(String.format("Excelente pontualidade (%d%%)", d.getScoreHIP2()));
        if (!d.temNegativosAtivos())
            r.addPontoPositivo("Sem pendências financeiras ativas");
        if (d.getScoreHSJ6() != null && d.getScoreHSJ6() > 700)
            r.addPontoPositivo(String.format("Baixo risco jurídico (Score %d)", d.getScoreHSJ6()));
        if (d.getIdadeMeses() != null && d.getIdadeMeses() > 120)
            r.addPontoPositivo("Empresa com mais de 10 anos de mercado");

        if (d.temPontualidadeRuim(60))
            r.addAlerta(String.format("Pontualidade abaixo da média (%d%%)",
                    d.getScoreHIP2() != null ? d.getScoreHIP2() : 0));
        if (d.getConsultasUltimos30Dias() > 3)
            r.addAlerta(String.format("%d consultas nos últimos 30 dias", d.getConsultasUltimos30Dias()));

        switch (r.getPerfil()) {
            case STARTUP -> r.addRecomendacao("Inicie com limite piloto para construir relacionamento");
            case RECUPERACAO -> r.addRecomendacao("Solicite garantia real ou venda antecipada");
            case RISCO_SOCIO -> r.addRecomendacao("Exija avalista PJ/PF sem restrições");
            case STANDARD -> r.addRecomendacao("Mantenha monitoramento regular");
            default -> {
            }
        }
    }

    private String sugerirLimite(PerfilCredito perfil) {
        return switch (perfil) {
            case PRIME -> "Até 30% do faturamento";
            case STANDARD -> "10% a 15% do faturamento";
            case ALERTA_OPERACIONAL -> "5% com garantia";
            case STARTUP -> "Limite piloto";
            case GIGANTE_BUROCRATICO -> "Até 20% do faturamento";
            case RECUPERACAO, FOME_CREDITO -> "À vista";
            case RISCO_SOCIO, RISCO_JURIDICO -> "Com garantias";
            case REJEICAO -> "Bloqueado";
        };
    }

    private String sugerirPrazo(PerfilCredito perfil) {
        return switch (perfil) {
            case PRIME -> "30/60/90 dias";
            case STANDARD -> "28 dias";
            case ALERTA_OPERACIONAL -> "14 dias";
            case STARTUP -> "7 dias";
            case GIGANTE_BUROCRATICO -> "30 dias";
            case RECUPERACAO, FOME_CREDITO -> "À vista";
            case RISCO_SOCIO, RISCO_JURIDICO -> "À vista/antecipado";
            case REJEICAO -> "N/A";
        };
    }

    private boolean scoreMaiorQue(Integer score, int valor) {
        return score != null && score > valor;
    }

    private boolean scoreMenorQue(Integer score, int valor) {
        return score != null && score < valor;
    }
}
