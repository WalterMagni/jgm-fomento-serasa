package com.portal.serasa.domain.model.prospeccao;

/** O que o comitê decidiu. Ver V77. */
public enum ResultadoComite {
    /** Segue para o contrato. */
    APROVADO,
    /** Volta para a análise: a analista decide de novo, numa nova versão da decisão. */
    DEVOLVIDO,
    /** Desfecho, com motivo. */
    REPROVADO
}
