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
    TRIAGEM(1),

    /** Uma analista assumiu. O nome dela fica no card. */
    EM_ANALISE(2),

    /**
     * Veredito humano favorável.
     *
     * <p>Estado de passagem: aprovar materializa o checklist e cai em DOCS_PENDENTES na mesma
     * transação. Por isso não é coluna do quadro — seria uma coluna permanentemente vazia.</p>
     */
    APROVADO(1),

    /** Coleta documental — a etapa que mais trava, e a que a planilha acompanhava pior. */
    DOCS_PENDENTES(10),

    /** Todo obrigatório resolvido. Aguarda o repasse para a habilitação. */
    DOCS_COMPLETOS(2),

    /** Saída da esteira: daqui em diante é a esteira de habilitação, fora deste escopo. */
    PRONTO_HABILITACAO(0),

    /** Terminal com motivo. Reabre para TRIAGEM quando o time decide reanalisar. */
    REPROVADO(0),

    /** Terminal com motivo. A aba homônima da planilha tem 193 linhas: é desfecho frequente. */
    REMOVIDO_RADAR(0);

    private final int prazoDiasUteis;

    EstagioProspeccao(int prazoDiasUteis) {
        this.prazoDiasUteis = prazoDiasUteis;
    }

    /** Prazo do estágio em dias úteis. Zero significa que o estágio não é cobrado por SLA. */
    public int prazoDiasUteis() {
        return prazoDiasUteis;
    }

    /** Terminais liberam o CNPJ para um card novo — ver o índice parcial da V56. */
    public boolean terminal() {
        return this == REPROVADO || this == REMOVIDO_RADAR || this == PRONTO_HABILITACAO;
    }

    /** Estágios em que o SLA corre. Fora deles o card não aparece na lista de atrasados. */
    public boolean contaSla() {
        return prazoDiasUteis > 0;
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
