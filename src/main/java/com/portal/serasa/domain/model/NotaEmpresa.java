package com.portal.serasa.domain.model;

/**
 * Nota que a equipe dá para a empresa, no estilo Nutri-Score. Não vem do Serasa nem de regra
 * automática: é a avaliação da casa sobre o cliente.
 */
public enum NotaEmpresa {
    /** Cliente excelente. */
    A,
    /** Bom cliente. */
    B,
    /** Operar com atenção. */
    C,
    /** Diretoria avalia antes de operar. */
    D,
    /** Saída de risco (V100, pedido do time em 2026-10-26). */
    E
}
