package com.portal.serasa.domain.model.notificacao;

/** O que aconteceu. A tela escolhe o ícone por aqui. */
public enum TipoNotificacao {
    CARD_CRIADO,
    PARECER_ESPERADO,
    PARECER_REGISTRADO,
    COMITE_COMPLETO,
    PENDENCIA_ABERTA,
    PENDENCIA_RESPONDIDA,
    MENCAO,
    COMENTARIO,
    DECISAO,
    /** Esteira de prospecção: o cliente respondeu à proposta. */
    RESPOSTA_CLIENTE,
    /** Esteira de prospecção: todos os documentos chegaram e o card foi para a conferência. */
    DOCUMENTOS_COMPLETOS,
    /** Judit: o detalhe de processo pedido ficou pronto (resumo de IA e movimentações). */
    JUDIT_PROCESSO_PRONTO,
    /** Judit: o detalhe de processo falhou ou passou do tempo. */
    JUDIT_PROCESSO_FALHOU,
    /** Judit: a consulta On Demand dos processos da empresa ficou pronta. */
    JUDIT_CONSULTA_PRONTA,
    /** Judit: a consulta On Demand falhou. */
    JUDIT_CONSULTA_FALHOU
}
