package com.portal.serasa.domain.model.prospeccao;

/** O que o cliente respondeu à proposta. Ver V75. */
public enum RespostaCliente {
    /** Segue para a documentação. */
    ACEITO,
    /** Desfecho: o card encerra em RECUSADO_CLIENTE. */
    RECUSADO,
    /** Volta para a análise com o pedido do cliente; a analista decide de novo. */
    CONTRAPROPOSTA
}
