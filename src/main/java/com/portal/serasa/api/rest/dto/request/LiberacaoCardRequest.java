package com.portal.serasa.api.rest.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Campos do card, iguais na criação e na edição.
 *
 * @param cedenteNome só é usado quando o CNPJ não está na base de empresas
 * @param version     obrigatório na edição: a versão que a tela recebeu ao abrir o card
 * @param proposta    números da Análise de Risco (AR) lida do PDF; nulo em card digitado à mão
 */
public record LiberacaoCardRequest(
        @NotBlank String cedenteCnpj,
        @Size(max = 300) String cedenteNome,
        @Size(max = 40) String tipoOperacao,
        @DecimalMin(value = "0.00", message = "Valor não pode ser negativo") BigDecimal valor,
        LocalDateTime prazo,
        @Size(max = 20000) String parecerOrigem,
        com.portal.serasa.domain.model.liberacao.PosicaoParecer posicaoOrigem,
        @Valid @Size(max = 200) List<Sacado> sacados,
        Long version,
        com.portal.serasa.domain.model.liberacao.PropostaAr proposta,
        /** Taxa da operação em % (2,5 = 2,5%). */
        @DecimalMin(value = "0.0000", message = "Taxa não pode ser negativa")
        @jakarta.validation.constraints.DecimalMax(value = "100.0000", message = "Taxa acima de 100%") BigDecimal taxa,
        /** TAC em R$. */
        @DecimalMin(value = "0.00", message = "TAC não pode ser negativa") BigDecimal tac,
        /** Em aberto não confirmado do cedente, em R$. */
        @DecimalMin(value = "0.00", message = "Tranche não pode ser negativa") BigDecimal tranche) {

    /** @param carteira linha do sacado na AR, quando veio do PDF */
    public record Sacado(
            String documento,
            @Size(max = 300) String nome,
            @DecimalMin(value = "0.00", message = "Valor não pode ser negativo") BigDecimal valor,
            com.portal.serasa.domain.model.liberacao.CarteiraSacado carteira) {
    }
}
