package com.portal.serasa.domain.model.prospeccao;

/** Como o card entrou na esteira. Ambos caem em {@link EstagioProspeccao#TRIAGEM}. */
public enum OrigemProspeccao {
    /** O comercial criou informando o CNPJ. */
    MANUAL,
    /**
     * Uma análise de crédito resultou {@code visaoCedente = SIM}.
     *
     * <p>O sinal é calculado a partir do Serasa, não é ato humano de aprovação — por isso ele
     * cria o card no primeiro estágio e o veredito humano vem depois. Os dois ficam lado a
     * lado e um nunca sobrescreve o outro, mesmo padrão já validado na praça de pagamento.</p>
     */
    AUTOMATICA
}
