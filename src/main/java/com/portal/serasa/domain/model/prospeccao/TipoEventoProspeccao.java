package com.portal.serasa.domain.model.prospeccao;

/** Tipos da timeline. Ver V57: a tabela é append-only. */
public enum TipoEventoProspeccao {
    CRIACAO,
    TRANSICAO,
    /** Contato com o cliente. Zera o relógio de silêncio do card. */
    COBRANCA,
    NOTA,
    DOC_RECEBIDO,
    DOC_VALIDADO,
    DOC_REJEITADO,
    DOC_DISPENSADO,
    ARQUIVO_ENVIADO,
    ARQUIVO_REMOVIDO,
    ANALISTA_ALTERADO,
    REABERTURA,
    /** Perfil comercial do card alterado. Ver V74. */
    DADOS_COMERCIAIS,
    /** Analista aprovou com limite ou reprovou. Ver V75. */
    DECISAO,
    /** Resposta do cliente à proposta. Ver V75. */
    ACEITE,
    /** Pedido de informação ou documento ao comercial. Ver V76. */
    PENDENCIA_ABERTA,
    PENDENCIA_RESPONDIDA,
    PENDENCIA_CANCELADA
}
