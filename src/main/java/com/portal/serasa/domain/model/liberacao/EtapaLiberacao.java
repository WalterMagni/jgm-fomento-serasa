package com.portal.serasa.domain.model.liberacao;

import java.util.EnumSet;
import java.util.Set;

/**
 * Etapas da esteira de liberação de operações, as cinco colunas da planilha do departamento.
 *
 * <p>A máquina de estados fica aqui, como em {@code EstagioProspeccao}, para a regra ser legível
 * de uma vez só. Quem pode disparar cada transição — e a trava dos pareceres do Comitê — mora em
 * {@code LiberacaoAutorizacao} e {@code LiberacaoService}; o enum só diz o que é caminho válido.</p>
 */
public enum EtapaLiberacao {

    /** A auxiliar abriu o card. Única etapa em que qualquer usuário edita. */
    ORIGEM,

    /** Aguardando o parecer de cada membro do Comitê. Só sai quando todos registraram. */
    COMITE,

    /** A analista pediu algo a alguém. Volta para decisão quando as pendências são respondidas. */
    PENDENCIA,

    APROVADO,

    REPROVADO;

    public boolean terminal() {
        return this == APROVADO || this == REPROVADO;
    }

    /**
     * Destinos válidos.
     *
     * <p>Os retrocessos são deliberados: devolver para a Origem corrige card mal preenchido, e
     * voltar da Pendência ao Comitê pede reanálise. Finalizado só volta ao Comitê, e isso é
     * reabertura — abre rodada nova de pareceres.</p>
     */
    public Set<EtapaLiberacao> destinos() {
        return switch (this) {
            case ORIGEM -> EnumSet.of(COMITE);
            case COMITE -> EnumSet.of(PENDENCIA, APROVADO, REPROVADO, ORIGEM);
            case PENDENCIA -> EnumSet.of(APROVADO, REPROVADO, COMITE);
            case APROVADO, REPROVADO -> EnumSet.of(COMITE);
        };
    }

    public boolean aceita(EtapaLiberacao destino) {
        return destinos().contains(destino);
    }

    /** Saídas do Comitê que dependem de todos os pareceres registrados. Devolver não depende. */
    public static boolean decisaoDoComite(EtapaLiberacao de, EtapaLiberacao para) {
        return de == COMITE && para != ORIGEM;
    }
}
