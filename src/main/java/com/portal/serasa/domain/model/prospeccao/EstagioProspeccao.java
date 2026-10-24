package com.portal.serasa.domain.model.prospeccao;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estágios da esteira de prospecção unificada, da abertura até o cedente habilitado.
 *
 * <p>Junta dois fluxos: a análise e a documentação da esteira original (V56) e a decisão com
 * limite, o aceite do cliente, o comitê e o contrato do Portal Comercial. Design em
 * {@code docs/plans/2026-10-11-esteira-unificada-design.md}; a ordem atual (Aprovado antes da
 * documentação, aceite depois do comitê) em {@code docs/plans/2026-10-13-esteira-aprovado-design.md}.</p>
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

    /** Entrou na esteira, ninguém pegou ainda. Porta de entrada do manual e da visão cedente. */
    NOVA(24),

    /** Uma analista assumiu e é a dona do card até o fim. */
    EM_ANALISE(24),

    /**
     * A analista aprovou com limite e condições; o comercial junta os documentos do cliente.
     *
     * <p>Sem prazo de relógio: depende do cliente enviar. Corre o silêncio — trinta dias corridos
     * sem retorno é atenção, quarenta e cinco encaminha para inerte. Com todos os obrigatórios
     * anexados o card segue sozinho para a documentação.</p>
     */
    APROVADO(0),

    /** A analista confere os documentos e envia ao comitê. */
    DOCUMENTACAO(0),

    /** Os membros do comitê dão parecer na rodada vigente. */
    COMITE(0),

    /**
     * Aprovado pelo comitê; falta o cliente responder à proposta.
     *
     * <p>Sem prazo de relógio: quem responde é o cliente, via comercial. O que corre é o silêncio.</p>
     */
    AGUARDANDO_ACEITE(0),

    /** O cliente aceitou; a analista fecha o contrato. */
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
     * <p>Vinte e quatro horas para alguém pegar a análise e vinte e quatro para decidir são os
     * números que o departamento de cadastro pratica. Pegar era doze até 2026-10-23, quando o time
     * avisou que o atraso aparecia antes da hora: o prazo da casa é de vinte e quatro.</p>
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
        return this == APROVADO || this == DOCUMENTACAO || this == AGUARDANDO_ACEITE;
    }

    /**
     * Do Aprovado ao Contrato o card pode ser cancelado: o cliente teve um problema que impede de
     * seguir (pedido do time em 2026-10-24). Cai em Reprovado, com motivo, pela ação própria —
     * antes da aprovação quem reprova é a decisão da análise.
     */
    public boolean cancelavel() {
        return this == APROVADO || this == DOCUMENTACAO || this == COMITE || this == AGUARDANDO_ACEITE || this == CONTRATO;
    }

    /** Sair daqui exige motivo preenchido. */
    public boolean exigeMotivo() {
        return this == REPROVADO || this == INERTE;
    }

    /**
     * Destinos válidos a partir deste estágio.
     *
     * <p>INERTE é destino de todo estágio em que o cliente ainda pode sumir; REPROVADO, de todo
     * estágio depois da aprovação, pelo cancelamento ({@link #cancelavel()}). Os retrocessos
     * (devolver para NOVA, voltar para a análise, devolver a documentação ao comercial, voltar do
     * contrato para o comitê) corrigem engano de operação sem exigir card novo.</p>
     *
     * <p>Terminais não têm destino: voltam só por reabertura, que é transição própria e
     * incrementa o contador de reaberturas.</p>
     */
    public Set<EstagioProspeccao> destinos() {
        return switch (this) {
            case NOVA -> EnumSet.of(EM_ANALISE, INERTE);
            case EM_ANALISE -> EnumSet.of(APROVADO, REPROVADO, NOVA, INERTE);
            case APROVADO -> EnumSet.of(DOCUMENTACAO, EM_ANALISE, INERTE, REPROVADO);
            case DOCUMENTACAO -> EnumSet.of(COMITE, APROVADO, EM_ANALISE, INERTE, REPROVADO);
            case COMITE -> EnumSet.of(AGUARDANDO_ACEITE, EM_ANALISE, REPROVADO);
            case AGUARDANDO_ACEITE -> EnumSet.of(CONTRATO, RECUSADO_CLIENTE, COMITE, INERTE, REPROVADO);
            case CONTRATO -> EnumSet.of(HABILITADO, COMITE, REPROVADO);
            case HABILITADO, REPROVADO, RECUSADO_CLIENTE, INERTE -> EnumSet.noneOf(EstagioProspeccao.class);
        };
    }

    public boolean aceita(EstagioProspeccao destino) {
        return destinos().contains(destino);
    }

    /**
     * Passagens que só acontecem por ação própria, porque exigem dado que a troca simples de
     * estágio não carrega.
     *
     * <p>Sair da análise aprovando ou reprovando é a <b>decisão</b> — limite, condições, parecer.
     * Entrar no comitê convoca os membros; sair dele é o <b>resultado</b>, que exige parecer. Sair
     * do aceite é a <b>resposta do cliente</b>, que fica registrada — a contraproposta volta ao
     * comitê numa rodada nova. Concluir exige o contato do contrato. Reprovar depois da aprovação é
     * ação própria (o cancelamento), que só a dona faz e que guarda o motivo. Arrastar o card não pode pular
     * esses registros.</p>
     *
     * @return o nome da ação que deve ser usada, ou nulo quando a troca simples basta
     */
    public String acaoPropria(EstagioProspeccao destino) {
        if (this == EM_ANALISE && (destino == APROVADO || destino == REPROVADO)) {
            return "a decisão da análise";
        }
        if (this == AGUARDANDO_ACEITE && (destino == CONTRATO || destino == RECUSADO_CLIENTE || destino == COMITE)) {
            return "a resposta do cliente";
        }
        if (destino == COMITE) {
            return "o envio ao comitê";
        }
        if (this == COMITE) {
            return "o resultado do comitê";
        }
        if (this == CONTRATO && destino == HABILITADO) {
            return "a conclusão do contrato";
        }
        if (destino == REPROVADO && cancelavel()) {
            return "a reprovação";
        }
        return null;
    }
}
