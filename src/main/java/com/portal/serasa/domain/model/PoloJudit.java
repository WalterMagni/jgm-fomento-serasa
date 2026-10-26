package com.portal.serasa.domain.model;

/** Polo pedido no On Demand. A Judit filtra pelo {@code side} da parte; cobra só o que devolve. */
public enum PoloJudit {
    /** A empresa como ré: o que pesa no crédito, e o pedido padrão. */
    PASSIVO("Passive"),
    /** A empresa como autora, num pedido à parte. */
    ATIVO("Active");

    private final String lado;

    PoloJudit(String lado) {
        this.lado = lado;
    }

    /** O valor de {@code search_params.filter.side} na Judit. */
    public String lado() {
        return lado;
    }
}
