package com.portal.serasa.domain.model.prospeccao;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estágios da esteira de prospecção unificada, da abertura até o cedente habilitado.
 *
 * <p>Junta dois fluxos: a análise e a documentação da esteira original (V56) e a decisão com
 * limite, o aceite do cliente, o comitê e o contrato do Portal Comercial. Design em
 * {@code docs/plans/2026-10-11-esteira-unificada-design.md}.</p>
 *
 * <p>A máquina de estados fica declarada aqui, e não espalhada pelo serviço, para que a regra
 * seja legível de uma vez só. Ela valida o <b>avanço</b> e permite <b>retrocessos declarados</b>:
 * quem opera são poucas pessoas da mesma equipe, e trava rígida demais só geraria contorno. O
 * que garante rastreabilidade é a timeline, que grava toda transição.</p>
 *
 * <p>O prazo é copiado para o card na transição, então mudar o número aqui não reescreve o
 * passado.</p>
 */
public enum EstagioProspeccao {

    /** Entrou na esteira, ninguém pegou ainda. Porta de entrada do manual e do automático. */
    NOVA(12),

    /** Uma analista assumiu e é a dona do card até o fim. */
    EM_ANALISE(24),

    /**
     * A analista aprovou com limite e condições; falta o cliente responder.
     *
     * <p>Sem prazo de relógio: quem responde é o cliente, via comercial. O que corre é o
     * silêncio, como na documentação.</p>
     */
    AGUARDANDO_ACEITE(0),

    /**
     * Coleta e conferência documental.
     *
     * <p>Sem prazo de propósito: depende do cliente enviar. Trinta dias corridos sem retorno é
     * atenção, quarenta e cinco encaminha para inerte.</p>
     */
    DOCUMENTACAO(0),

    /** Os membros do comitê dão parecer na rodada vigente. */
    COMITE(0),

    /** Aprovado pelo comitê; a analista fecha o contrato. */
    CONTRATO(0),

    /** Fim do fluxo: cedente pronto para operar. Daqui em diante vive na esteira de liberação. */
    HABILITADO(0),

    /** Terminal com motivo. Reabre para NOVA quando o time decide reanalisar. */
    REPROVADO(0),

    /** O cliente não aceitou a proposta. */
    RECUSADO_CLIENTE(0),

    /**
     * Terminal com motivo: cliente que parou de responder.
     *
     * <p>A aba homônima da planilha tem 193 linhas — é desfecho frequente, não exceção.</p>
     */
    INERTE(0);

    private final int prazoHorasUteis;

    EstagioProspeccao(int prazoHorasUteis) {
        this.prazoHorasUteis = prazoHorasUteis;
    }

    /**
     * Prazo do estágio em horas úteis. Zero significa estágio sem prazo.
     *
     * <p>Doze horas para alguém pegar a análise e vinte e quatro para decidir são os números que o
     * departamento de cadastro pratica.</p>
     */
    public int prazoHorasUteis() {
        return prazoHorasUteis;
    }

    /** Terminais liberam o CNPJ para um card novo — ver o índice parcial da V74. */
    public boolean terminal() {
        return this == HABILITADO || this == REPROVADO || this == RECUSADO_CLIENTE || this == INERTE;
    }

    /** Estágios em que o SLA corre. Fora deles o card não aparece na lista de atrasados. */
    public boolean contaSla() {
        return prazoHorasUteis > 0;
    }

    /** Estágios em que quem deve resposta é o cliente: ali corre o relógio de silêncio. */
    public boolean contaSilencio() {
        return this == AGUARDANDO_ACEITE || this == DOCUMENTACAO;
    }

    /** Sair daqui exige motivo preenchido. */
    public boolean exigeMotivo() {
        return this == REPROVADO || this == INERTE;
    }

    /**
     * Destinos válidos a partir deste estágio.
     *
     * <p>INERTE é destino de todo estágio em que o cliente ainda pode sumir. Os retrocessos
     * (devolver para NOVA, voltar da documentação para a análise, voltar do contrato para o
     * comitê) corrigem engano de operação sem exigir card novo.</p>
     *
     * <p>Terminais não têm destino: voltam só por reabertura, que é transição própria e
     * incrementa o contador de reaberturas.</p>
     */
    public Set<EstagioProspeccao> destinos() {
        return switch (this) {
            case NOVA -> EnumSet.of(EM_ANALISE, INERTE);
            case EM_ANALISE -> EnumSet.of(AGUARDANDO_ACEITE, REPROVADO, NOVA, INERTE);
            case AGUARDANDO_ACEITE -> EnumSet.of(DOCUMENTACAO, RECUSADO_CLIENTE, EM_ANALISE, INERTE);
            case DOCUMENTACAO -> EnumSet.of(COMITE, EM_ANALISE, INERTE);
            case COMITE -> EnumSet.of(CONTRATO, EM_ANALISE, REPROVADO);
            case CONTRATO -> EnumSet.of(HABILITADO, COMITE);
            case HABILITADO, REPROVADO, RECUSADO_CLIENTE, INERTE -> EnumSet.noneOf(EstagioProspeccao.class);
        };
    }

    public boolean aceita(EstagioProspeccao destino) {
        return destinos().contains(destino);
    }
}
