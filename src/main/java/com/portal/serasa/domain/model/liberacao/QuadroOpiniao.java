package com.portal.serasa.domain.model.liberacao;

/**
 * Os quadrados de cada sacado: O (Origem), C (Comitê) e D (Diretoria).
 *
 * <p>O e C são opiniões, nos campos de opinião do sacado. D é a decisão — a que calcula o
 * resultado do card — e mora nos campos de situação. Os três têm um parecer em texto.</p>
 */
public enum QuadroOpiniao {

    /** Marcado por qualquer pessoa, com o card na Origem. */
    ORIGEM,

    /** Marcado por analista, com o card no Comitê ou em Pendência. */
    COMITE,

    /** A decisão do sacado. Marcada por analista, com o card no Comitê ou em Pendência. */
    DIRETORIA;

    public String letra() {
        return switch (this) {
            case ORIGEM -> "O";
            case COMITE -> "C";
            case DIRETORIA -> "D";
        };
    }
}
