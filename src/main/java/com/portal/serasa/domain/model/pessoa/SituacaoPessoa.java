package com.portal.serasa.domain.model.pessoa;

import java.math.BigDecimal;

/**
 * Situação da pessoa física na última consulta Serasa, para a triagem da lista.
 *
 * <p>Cortes combinados com o crédito em 2026-10-12: crítico a partir de três ocorrências ou
 * com dívida acima de R$ 50 mil, mesmo que seja uma ocorrência só.</p>
 */
public enum SituacaoPessoa {
    LIMPO,
    COM_RESTRICAO,
    CRITICO;

    /** A partir de quantas ocorrências a pessoa é crítica. */
    public static final int OCORRENCIAS_CRITICAS = 3;

    /** Dívida acima deste valor torna a pessoa crítica. */
    public static final BigDecimal DIVIDA_CRITICA = new BigDecimal("50000");

    public static SituacaoPessoa classificar(int ocorrencias, BigDecimal divida) {
        if (ocorrencias <= 0) {
            return LIMPO;
        }
        if (ocorrencias >= OCORRENCIAS_CRITICAS) {
            return CRITICO;
        }
        if (divida != null && divida.compareTo(DIVIDA_CRITICA) > 0) {
            return CRITICO;
        }
        return COM_RESTRICAO;
    }
}
