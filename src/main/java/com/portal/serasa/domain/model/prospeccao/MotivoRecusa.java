package com.portal.serasa.domain.model.prospeccao;

/**
 * Motivos de recusa, extraídos dos padrões que já se repetem na coluna
 * {@code MOTIVO DA RECUSA} da planilha de controle.
 *
 * <p>Enum fechado, e não texto livre, porque texto livre nunca vira métrica: a planilha
 * preenche essa coluna há um ano e ninguém consegue responder "qual o motivo mais comum".
 * A observação livre continua existindo ao lado, no card.</p>
 */
public enum MotivoRecusa {
    QUANTIDADE_DE_RESTRICOES,
    SEGMENTO,
    PEFIN_COM_FUNDO,
    PROCESSO_COM_FIDC,
    RECUPERACAO_JUDICIAL,
    DECISAO_DIRETORIA,
    /** Exige observação preenchida — é a válvula de escape, não o default. */
    OUTRO;

    /** Como o time lê o motivo — o mesmo texto da tela. */
    public String rotulo() {
        return switch (this) {
            case QUANTIDADE_DE_RESTRICOES -> "Quantidade de restrições";
            case SEGMENTO -> "Segmento";
            case PEFIN_COM_FUNDO -> "Pefin com fundo";
            case PROCESSO_COM_FIDC -> "Processo com FIDC";
            case RECUPERACAO_JUDICIAL -> "Recuperação judicial";
            case DECISAO_DIRETORIA -> "Decisão da diretoria";
            case OUTRO -> "Outro";
        };
    }
}
