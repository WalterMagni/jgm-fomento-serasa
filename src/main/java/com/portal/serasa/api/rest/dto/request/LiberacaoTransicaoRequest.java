package com.portal.serasa.api.rest.dto.request;

import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Mover o card.
 *
 * @param de          etapa em que a tela viu o card; recusa se outra pessoa já o moveu
 * @param pendencias  obrigatória ao entrar em Pendência, a menos que já haja pendência aberta
 */
public record LiberacaoTransicaoRequest(
        @NotNull EtapaLiberacao de,
        @NotNull EtapaLiberacao para,
        @Valid @Size(max = 20) List<LiberacaoPendenciaRequest> pendencias,
        @Size(max = 5000) String observacao,
        /** Ao aprovar: situação de cada sacado. Reprovar reprova todos sem precisar mandar. */
        @Valid @Size(max = 200) List<DecisaoSacado> decisoes) {

    public record DecisaoSacado(@jakarta.validation.constraints.NotBlank String documento,
                                com.portal.serasa.domain.model.liberacao.ResultadoLiberacao situacao,
                                @jakarta.validation.constraints.DecimalMin("0.00") java.math.BigDecimal valorAprovado) {
    }
}
