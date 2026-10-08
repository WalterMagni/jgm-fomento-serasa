package com.portal.serasa.domain.model.liberacao;

public enum TipoEventoLiberacao {
    CRIACAO,
    /** Um evento por campo alterado, com valor antes e depois. */
    EDICAO,
    TRANSICAO,
    /** Finalizado que voltou ao Comitê. Abre rodada nova de pareceres. */
    REABERTURA,
    PARECER,
    PENDENCIA_ABERTA,
    PENDENCIA_RESPONDIDA,
    EXCLUSAO
}
