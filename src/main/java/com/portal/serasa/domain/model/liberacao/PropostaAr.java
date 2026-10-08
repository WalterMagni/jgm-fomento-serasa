package com.portal.serasa.domain.model.liberacao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Números da Análise de Risco (AR) do sistema de operações, guardados no card como foto do
 * momento em que a proposta foi importada. Não se recalcula: é o que o Comitê viu.
 *
 * <p>"Vincendos" é o "a vencer" do relatório. Percentuais vêm como o relatório imprime
 * (239,4184 = 239,4184%).</p>
 *
 * <p>O construtor normaliza o que chega (do PDF, da tela ou do banco): data que não existe vira
 * nula, e valores ganham escala fixa — 2 casas em dinheiro e prazo, 4 em percentual —, para
 * {@code equals} não ver mudança onde só mudou a escala (5077 e 5077.00).</p>
 *
 * @param emitidaEm data e hora do relatório, ISO ("2026-10-08T15:59:13"); nula se inválida
 * @param grupo     nome do grupo econômico no relatório, ou nulo quando vem em branco
 */
public record PropostaAr(
        String clienteCodigo,
        String emitidaEm,
        String grupo,
        BigDecimal limiteIndividual,
        BigDecimal limiteGrupo,
        Integer qtdLiberados,
        BigDecimal prazoMedio,
        BigDecimal faceLiberados,
        BigDecimal desconto,
        BigDecimal liquido,
        Integer qtdTotal,
        BigDecimal valorTotal,
        BigDecimal liquidados,
        BigDecimal liquidadosEmAtraso,
        BigDecimal recomprados,
        BigDecimal vencidos,
        BigDecimal vincendos,
        BigDecimal emAberto,
        BigDecimal comprometimentoAtual,
        BigDecimal comprometimentoGrupoAtual,
        BigDecimal concentracaoAtual,
        BigDecimal comprometimentoApos,
        BigDecimal comprometimentoGrupoApos,
        BigDecimal concentracaoApos) {

    public PropostaAr {
        emitidaEm = dataValida(emitidaEm);
        limiteIndividual = escala(limiteIndividual, 2);
        limiteGrupo = escala(limiteGrupo, 2);
        prazoMedio = escala(prazoMedio, 2);
        faceLiberados = escala(faceLiberados, 2);
        desconto = escala(desconto, 2);
        liquido = escala(liquido, 2);
        valorTotal = escala(valorTotal, 2);
        liquidados = escala(liquidados, 2);
        liquidadosEmAtraso = escala(liquidadosEmAtraso, 2);
        recomprados = escala(recomprados, 2);
        vencidos = escala(vencidos, 2);
        vincendos = escala(vincendos, 2);
        emAberto = escala(emAberto, 2);
        comprometimentoAtual = escala(comprometimentoAtual, 4);
        comprometimentoGrupoAtual = escala(comprometimentoGrupoAtual, 4);
        concentracaoAtual = escala(concentracaoAtual, 4);
        comprometimentoApos = escala(comprometimentoApos, 4);
        comprometimentoGrupoApos = escala(comprometimentoGrupoApos, 4);
        concentracaoApos = escala(concentracaoApos, 4);
    }

    /** {@link #emitidaEm()} já interpretada; nula quando o relatório não trouxe data. */
    public LocalDateTime emitidaEmData() {
        return emitidaEm == null ? null : LocalDateTime.parse(emitidaEm);
    }

    static BigDecimal escala(BigDecimal valor, int casas) {
        return valor == null ? null : valor.setScale(casas, RoundingMode.HALF_UP);
    }

    private static String dataValida(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(texto.trim()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException erro) {
            return null;
        }
    }
}
