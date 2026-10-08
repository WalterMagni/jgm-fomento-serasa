package com.portal.serasa.domain.model.liberacao;

import java.util.Collection;

/** Decisão de um sacado, e o resultado do card que sai das decisões dos sacados. Ver V70. */
public enum ResultadoLiberacao {
    APROVADO,
    REPROVADO,
    PARCIAL;

    /**
     * Resultado do card: todos aprovados = APROVADO, todos reprovados = REPROVADO, qualquer
     * mistura ou parcial = PARCIAL. Nulo quando não há decisão nenhuma.
     */
    public static ResultadoLiberacao doCard(Collection<ResultadoLiberacao> situacoes) {
        if (situacoes.isEmpty()) {
            return null;
        }
        if (situacoes.stream().allMatch(situacao -> situacao == APROVADO)) {
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
        };
    }
}
