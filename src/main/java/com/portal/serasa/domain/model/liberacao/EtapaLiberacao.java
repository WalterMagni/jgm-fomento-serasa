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
 * <p>Histórico: até a V70 havia Aprovado e Reprovado como fim de linha; a V70 juntou os dois em
 * Finalizados. Em 2026-10-12 o time pediu as colunas de volta, agora como passos: a analista
 * decide Aprovado ou Reprovado; o aprovado segue para Assinatura e o reprovado é finalizado.
 * O resultado (aprovado, reprovado, parcial) continua saindo das decisões dos sacados.</p>
 *
 * <p>Em 2026-10-14 entrou a Diretoria: o Comitê decide sozinho ou manda para ela, que dá a palavra
 * final. Ver docs/plans/2026-10-14-liberacao-diretoria-design.md.</p>
 */
public enum EtapaLiberacao {

    /** A auxiliar abriu o card. Única etapa em que qualquer usuário edita. */
    ORIGEM,

    /** Aguardando parecer do Comitê. Sai com pelo menos um parecer registrado. */
    COMITE,

    /** A analista pediu algo a alguém antes de decidir. */
    PENDENCIA,

    /**
     * O Comitê mandou para a Diretoria decidir, com o motivo. A Diretoria só aprova ou reprova:
     * não devolve. Ver V88.
     */
    DIRETORIA,

    /** Decidido a favor, inteiro ou em parte. Segue para a assinatura do contrato. */
    APROVADO,

    /** Decidido contra. Só falta finalizar. */
    REPROVADO,

    /** Aprovado, aguardando a assinatura do contrato. */
    ASSINATURA,

    /** Fim de linha. Sai do quadro quando arquivado. */
    FINALIZADO;

    /** A decisão, do Comitê ou da Diretoria: é aqui que o resultado do card é calculado. */
    public boolean decisao() {
        return this == APROVADO || this == REPROVADO;
    }

    /** Já decidido: o prazo de decisão para de correr e os sacados não mudam sem reabrir. */
    public boolean decidida() {
        return this == APROVADO || this == REPROVADO || this == ASSINATURA || this == FINALIZADO;
    }

    public boolean terminal() {
        return this == FINALIZADO;
    }

    /**
     * Destinos válidos.
     *
     * <p>Os retrocessos são deliberados: devolver para a Origem corrige card mal preenchido, e
     * voltar da Pendência ao Comitê pede reanálise. De qualquer etapa já decidida o card só volta
     * ao Comitê, e isso é reabertura — abre rodada nova de pareceres.</p>
     */
    public Set<EtapaLiberacao> destinos() {
        return switch (this) {
            case ORIGEM -> EnumSet.of(COMITE);
            case COMITE -> EnumSet.of(PENDENCIA, DIRETORIA, APROVADO, REPROVADO, ORIGEM);
            case PENDENCIA -> EnumSet.of(DIRETORIA, APROVADO, REPROVADO, COMITE);
            case DIRETORIA -> EnumSet.of(APROVADO, REPROVADO);
            case APROVADO -> EnumSet.of(ASSINATURA, COMITE);
            case REPROVADO -> EnumSet.of(FINALIZADO, COMITE);
            case ASSINATURA -> EnumSet.of(FINALIZADO, COMITE);
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

    /** Voltar de etapa decidida para o Comitê: rodada nova, resultado desfeito. */
    public static boolean reabertura(EtapaLiberacao de, EtapaLiberacao para) {
        return de.decidida() && para == COMITE;
    }
}
