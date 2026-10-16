package com.portal.serasa.domain.model;

/** Onde está uma consulta à Judit. Só o detalhe de processo passa por PENDENTE: é a única assíncrona. */
public enum SituacaoConsultaJudit {
    /** Pedido feito (e cobrado); o backend está esperando a Judit buscar no tribunal. */
    PENDENTE,
    CONCLUIDA,
    /** A Judit devolveu erro ou não terminou no tempo. O custo continua contado: o pedido foi cobrado. */
    FALHOU
}
