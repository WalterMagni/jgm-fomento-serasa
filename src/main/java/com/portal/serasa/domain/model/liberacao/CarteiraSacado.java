package com.portal.serasa.domain.model.liberacao;

import java.math.BigDecimal;

/**
 * Linha do sacado na Análise de Risco (AR): o que ele já tem com o cedente na carteira.
 *
 * <p>Valores com 2 casas fixas, pelo mesmo motivo de {@link PropostaAr}.</p>
 *
 * @param titulos quantos títulos dele entram nesta proposta
 */
public record CarteiraSacado(
        Integer titulos,
        BigDecimal vencidos,
        BigDecimal vincendos,
        BigDecimal abertos,
        BigDecimal liquidados,
        BigDecimal recomprados) {

    public CarteiraSacado {
        vencidos = PropostaAr.escala(vencidos, 2);
        vincendos = PropostaAr.escala(vincendos, 2);
        abertos = PropostaAr.escala(abertos, 2);
        liquidados = PropostaAr.escala(liquidados, 2);
        recomprados = PropostaAr.escala(recomprados, 2);
    }
}
