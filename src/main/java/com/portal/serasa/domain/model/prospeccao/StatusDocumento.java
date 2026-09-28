package com.portal.serasa.domain.model.prospeccao;

import java.util.EnumSet;
import java.util.Set;

/**
 * Status de um item do checklist.
 *
 * <p>{@link #RECEBIDO} e {@link #VALIDADO} são estados diferentes de propósito. Na planilha a
 * observação do documento é conferência de conteúdo, não recebimento — "OK - CRC válido",
 * "OK - Sofisa, Bradesco, Itaú", "OK - 7 Clientes". Alguém abre e lê antes de dar por
 * resolvido, e essa leitura é trabalho que merece estado próprio.</p>
 *
 * <p>{@link #NAO_APLICAVEL} e {@link #DISPENSADO} são a saída que reconcilia "documento
 * obrigatório trava o avanço" com a realidade: a certidão simplificada é retirada na JUCESP e
 * só existe em São Paulo, e a planilha já registra à mão "Não tem, cliente de MG".</p>
 */
public enum StatusDocumento {

    PENDENTE,

    /** O cliente mandou. Ainda não foi conferido. */
    RECEBIDO,

    /** Conferido e aceito. */
    VALIDADO,

    /** Conferido e recusado — errado, vencido ou ilegível. Exige motivo e volta a ser cobrado. */
    REJEITADO,

    /** Não se aplica a esta empresa. Automático para item com UF fixa fora daquela UF. */
    NAO_APLICAVEL,

    /** Exigência da qual se abriu mão por decisão humana. Exige motivo e fica na timeline. */
    DISPENSADO;

    private static final Set<StatusDocumento> RESOLVIDOS =
            EnumSet.of(VALIDADO, NAO_APLICAVEL, DISPENSADO);

    /** Um obrigatório só deixa de travar o avanço quando chega a um destes. */
    public boolean resolvido() {
        return RESOLVIDOS.contains(this);
    }

    /** Exige texto justificando. */
    public boolean exigeMotivo() {
        return this == REJEITADO || this == DISPENSADO;
    }
}
