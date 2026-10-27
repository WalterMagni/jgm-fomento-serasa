package com.portal.serasa.domain.model.liberacao;

import java.util.Collection;

/** Decisão de um sacado, e o resultado do card que sai das decisões dos sacados. Ver V70. */
public enum ResultadoLiberacao {
    APROVADO,
    REPROVADO,
    PARCIAL,
    /**
     * Aprovado por amostragem (pedido do time em 2026-10-27): no C e no D conta como aprovado — o
     * valor inteiro entra no aprovado e o card fica aprovado —, só marcado à parte. O resultado do
     * card nunca é Amostragem: sai como Aprovado.
     */
    AMOSTRAGEM;

    /** Aprova o valor inteiro do sacado: Aprovado ou Amostragem. */
    public boolean aprovaInteiro() {
        return this == APROVADO || this == AMOSTRAGEM;
    }

    /**
     * Resultado do card: todos aprovados (Amostragem conta) = APROVADO, todos reprovados =
     * REPROVADO, qualquer mistura ou parcial = PARCIAL. Nulo quando não há decisão nenhuma.
     */
    public static ResultadoLiberacao doCard(Collection<ResultadoLiberacao> situacoes) {
        if (situacoes.isEmpty()) {
            return null;
        }
        if (situacoes.stream().allMatch(ResultadoLiberacao::aprovaInteiro)) {
            return APROVADO;
        }
        if (situacoes.stream().allMatch(situacao -> situacao == REPROVADO)) {
            return REPROVADO;
        }
        return PARCIAL;
    }

    public String rotulo() {
        return switch (this) {
            case APROVADO -> "Aprovado";
            case REPROVADO -> "Reprovado";
            case PARCIAL -> "Parcialmente aprovado";
            case AMOSTRAGEM -> "Amostragem";
        };
    }
}
