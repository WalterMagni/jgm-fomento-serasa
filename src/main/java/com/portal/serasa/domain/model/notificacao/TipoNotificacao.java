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
    DOCUMENTOS_COMPLETOS
}
