package com.portal.serasa.domain.model.liberacao;

/**
 * Os quadrados de cada sacado: O (Origem), C (Comitê) e D (Diretoria).
 *
 * <p>Desde a V88, C é a decisão do Comitê e D a da Diretoria; a situação do sacado é a decisão
 * final, copiada de um dos dois quando o card é decidido. O é o parecer dos operadores: saiu da
 * tela na V88 e voltou em 2026-10-15, como opinião que não entra no resultado. Os três têm um
 * parecer em texto.</p>
 */
public enum QuadroOpiniao {

    /**
     * Parecer dos operadores. Marcado por analista antes da decisão (Origem, Comitê ou Pendência).
     * O nome continua ORIGEM porque é o que está gravado no banco e no histórico.
     */
    ORIGEM,

    /** Decisão do Comitê. Marcada por analista, com o card no Comitê ou em Pendência. */
    COMITE,

    /** Decisão da Diretoria. Marcada por quem tem o perfil, com o card na Diretoria. */
    DIRETORIA;

    public String letra() {
        return switch (this) {
            case ORIGEM -> "O";
            case COMITE -> "C";
            case DIRETORIA -> "D";
        };
    }
}
