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
        Long version) {

    public record Sacado(
            String documento,
            @Size(max = 300) String nome,
            @DecimalMin(value = "0.00", message = "Valor não pode ser negativo") BigDecimal valor) {
    }
}
