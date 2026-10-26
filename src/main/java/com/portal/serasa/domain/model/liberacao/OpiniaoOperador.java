package com.portal.serasa.domain.model.liberacao;

/**
 * O quadrado O de um sacado: a opinião dos operadores. Não é decisão — não entra no resultado do
 * card, que sai do C e do D ({@link ResultadoLiberacao}).
 *
 * <p>Tem uma opção a mais que as decisões: Amostragem (pedido do time em 2026-10-26). Os nomes das
 * outras três são os mesmos de {@link ResultadoLiberacao}: é o que já está gravado em
 * {@code opiniao_origem}.</p>
 */
public enum OpiniaoOperador {
    APROVADO,
    PARCIAL,
    REPROVADO,
    AMOSTRAGEM;

    public String rotulo() {
        return switch (this) {
            case APROVADO -> "Aprovado";
            case PARCIAL -> "Parcialmente aprovado";
            case REPROVADO -> "Reprovado";
            case AMOSTRAGEM -> "Amostragem";
        };
    }
}
