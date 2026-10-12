package com.portal.serasa.domain.model.pessoa;

/**
 * Tipos de restrição do relatório Serasa PF que entram na situação da pessoa.
 *
 * <p>{@code chave} é o nome do bloco no JSON guardado em {@code person_analysis}: os cinco
 * primeiros vêm de {@code negative_summary}, ações e falências de {@code facts}. É a mesma
 * soma que a tela de análise da pessoa faz para o total de ocorrências.</p>
 */
public enum TipoRestricao {
    PEFIN("pefin"),
    REFIN("refin"),
    CHEQUE("check"),
    PROTESTO("notary"),
    DIVIDA_VENCIDA("collectionRecords"),
    ACAO_JUDICIAL("judgementFilings"),
    FALENCIA("bankrupts");

    private final String chave;

    TipoRestricao(String chave) {
        this.chave = chave;
    }

    public String chave() {
        return chave;
    }
}
