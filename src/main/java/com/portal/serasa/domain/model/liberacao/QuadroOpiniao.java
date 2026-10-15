package com.portal.serasa.domain.model.liberacao;

/**
 * Os quadrados de cada sacado: O (Origem), C (Comitê) e D (Diretoria).
 *
 * <p>Desde a V88, C é a decisão do Comitê e D a da Diretoria; a situação do sacado é a decisão
 * final, copiada de um dos dois quando o card é decidido. O é a opinião da Origem, que saiu da
 * tela — fica pelos dados antigos. Os três têm um parecer em texto.</p>
 */
public enum QuadroOpiniao {

    /** Marcado por qualquer pessoa, com o card na Origem. Fora da tela desde a V88. */
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
