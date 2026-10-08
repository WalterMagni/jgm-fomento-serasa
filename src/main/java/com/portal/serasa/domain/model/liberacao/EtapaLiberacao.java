package com.portal.serasa.domain.model.liberacao;

import java.util.EnumSet;
import java.util.Set;

/**
 * Etapas da esteira de liberação de operações.
 *
 * <p>A máquina de estados fica aqui, como em {@code EstagioProspeccao}, para a regra ser legível
 * de uma vez só. Quem pode disparar cada transição — e a regra do parecer do Comitê — mora em
 * {@code LiberacaoAutorizacao} e {@code LiberacaoService}; o enum só diz o que é caminho válido.</p>
 *
 * <p>Até a V70 havia Aprovado e Reprovado como colunas separadas. Viraram Finalizados: a decisão
 * é por sacado, e o resultado do card (aprovado, reprovado, parcial) sai deles.</p>
 */
public enum EtapaLiberacao {

    /** A auxiliar abriu o card. Única etapa em que qualquer usuário edita. */
    ORIGEM,

    /** Aguardando parecer do Comitê. Sai com pelo menos um parecer registrado. */
    COMITE,

    /** A analista pediu algo a alguém antes de decidir. */
    PENDENCIA,

    /** Decidido. O resultado está no card e a decisão de cada sacado, no sacado. */
    FINALIZADO;

    public boolean terminal() {
        return this == FINALIZADO;
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
            case COMITE -> EnumSet.of(PENDENCIA, FINALIZADO, ORIGEM);
            case PENDENCIA -> EnumSet.of(FINALIZADO, COMITE);
            case FINALIZADO -> EnumSet.of(COMITE);
        };
    }

    public boolean aceita(EtapaLiberacao destino) {
        return destinos().contains(destino);
    }

    /** Saídas do Comitê que pedem parecer registrado. Devolver não pede. */
    public static boolean decisaoDoComite(EtapaLiberacao de, EtapaLiberacao para) {
        return de == COMITE && para != ORIGEM;
    }
}
