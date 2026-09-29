package com.portal.serasa.domain.model.prospeccao;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estágios da esteira de prospecção, da solicitação da análise até a documentação completa.
 *
 * <p>A máquina de estados fica declarada aqui, e não espalhada pelo serviço, para que a regra
 * seja legível de uma vez só. Ela valida o <b>avanço</b> e permite o <b>retrocesso</b>: quem
 * opera são poucas pessoas da mesma equipe, e trava rígida demais só geraria contorno. O que
 * garante rastreabilidade é a timeline, que grava toda transição, não a restrição.</p>
 *
 * <p>Os prazos são o default proposto em
 * {@code docs/plans/2026-09-28-esteira-prospeccao-defaults.md} e ainda não foram confirmados
 * pelo time. O prazo é copiado para o card na transição, então mudar o número aqui não
 * reescreve o passado.</p>
 */
public enum EstagioProspeccao {

    /** Entrou na esteira, ninguém pegou ainda. Porta de entrada do manual e do automático. */
    TRIAGEM(12),

    /** Uma analista assumiu. O nome dela fica no card. */
    EM_ANALISE(24),

    /**
     * Veredito humano favorável.
     *
     * <p>Estado de passagem: aprovar materializa o checklist e cai em DOCS_PENDENTES na mesma
     * transação. Por isso não é coluna do quadro — seria uma coluna permanentemente vazia.</p>
     */
    APROVADO(0),

    /**
     * Coleta documental.
     *
     * <p>Sem prazo de propósito: depende do cliente enviar, e o time não cobra a si mesmo por isso.
     * O que corre aqui é o relógio do silêncio — trinta dias corridos sem retorno é atenção,
     * quarenta e cinco encaminha para inerte.</p>
     */
    DOCS_PENDENTES(0),

    /** Todo obrigatório resolvido. Aguarda o repasse para a habilitação. */
    DOCS_COMPLETOS(24),

    /** Saída da esteira: daqui em diante é a esteira de habilitação, fora deste escopo. */
    PRONTO_HABILITACAO(0),

    /** Terminal com motivo. Reabre para TRIAGEM quando o time decide reanalisar. */
    REPROVADO(0),

    /**
     * Terminal com motivo. O time chama de <b>inerte</b>: cliente que parou de responder.
     *
     * <p>A aba homônima da planilha tem 193 linhas — é desfecho frequente, não exceção.</p>
     */
    REMOVIDO_RADAR(0);

    private final int prazoHorasUteis;

    EstagioProspeccao(int prazoHorasUteis) {
        this.prazoHorasUteis = prazoHorasUteis;
    }

    /**
     * Prazo do estágio em horas úteis. Zero significa estágio sem prazo.
     *
     * <p>Doze horas para alguém pegar a análise e vinte e quatro para decidir são os números que o
     * departamento de cadastro pratica. A coleta de documentos fica em zero porque depende do
     * cliente — ali o que corre é o silêncio, não o relógio do estágio.</p>
     */
    public int prazoHorasUteis() {
        return prazoHorasUteis;
    }

    /** Terminais liberam o CNPJ para um card novo — ver o índice parcial da V56. */
    public boolean terminal() {
        return this == REPROVADO || this == REMOVIDO_RADAR || this == PRONTO_HABILITACAO;
    }

    /** Estágios em que o SLA corre. Fora deles o card não aparece na lista de atrasados. */
    public boolean contaSla() {
        return prazoHorasUteis > 0;
    }

    /** Sair daqui exige motivo preenchido. */
    public boolean exigeMotivo() {
        return this == REPROVADO || this == REMOVIDO_RADAR;
    }

    /**
     * Destinos válidos a partir deste estágio.
     *
     * <p>REMOVIDO_RADAR é destino de qualquer não-terminal e por isso não aparece item a item.
     * Os retrocessos declarados (devolver para TRIAGEM, voltar de DOCS_COMPLETOS) são
     * deliberados: corrigem engano de operação sem exigir card novo.</p>
     */
    public Set<EstagioProspeccao> destinos() {
        return switch (this) {
            case TRIAGEM -> EnumSet.of(EM_ANALISE, REMOVIDO_RADAR);
            case EM_ANALISE -> EnumSet.of(APROVADO, REPROVADO, TRIAGEM, REMOVIDO_RADAR);
            case APROVADO -> EnumSet.of(DOCS_PENDENTES, EM_ANALISE, REMOVIDO_RADAR);
            case DOCS_PENDENTES -> EnumSet.of(DOCS_COMPLETOS, APROVADO, REMOVIDO_RADAR);
            case DOCS_COMPLETOS -> EnumSet.of(PRONTO_HABILITACAO, DOCS_PENDENTES, REMOVIDO_RADAR);
            // Terminais só voltam por reabertura explícita, que é transição própria e
            // incrementa o contador de reaberturas.
            case PRONTO_HABILITACAO -> EnumSet.noneOf(EstagioProspeccao.class);
            case REPROVADO, REMOVIDO_RADAR -> EnumSet.of(TRIAGEM);
        };
    }

    public boolean aceita(EstagioProspeccao destino) {
        return destinos().contains(destino);
    }
}
