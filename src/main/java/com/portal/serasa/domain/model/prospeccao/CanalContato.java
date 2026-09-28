package com.portal.serasa.domain.model.prospeccao;

/**
 * Canal da cobrança. Substitui as colunas {@code SOLICITAÇÃO POR EMAIL} e
 * {@code SOLICITAÇÃO POR WHATS} da aba DOC'S PENDENTES, que guardavam só a última de cada.
 */
public enum CanalContato {
    EMAIL,
    WHATSAPP,
    LIGACAO,
    REUNIAO,
    /** Evento gerado pelo próprio sistema, não por uma pessoa. */
    SISTEMA
}
