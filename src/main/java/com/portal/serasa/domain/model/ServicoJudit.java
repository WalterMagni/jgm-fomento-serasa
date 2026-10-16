package com.portal.serasa.domain.model;

/** O que foi pedido à Judit numa consulta. Cada um tem preço próprio — ver {@code judit.*} no application.yml. */
public enum ServicoJudit {
    /** {@code /lawsuits/synthetic}: total e contagens agregadas. Preço fixo por documento. */
    RESUMO,
    /** {@code /lawsuits}: a lista de processos. Cobrado a cada 1.000 processos devolvidos. */
    PROCESSOS,
    /**
     * {@code /requests} por CNJ com {@code judit_ia: ["summary"]}: um processo direto no tribunal, com
     * todas as movimentações e resumo de IA. Assíncrono. Preço fixo por processo.
     */
    DETALHE
}
