package com.portal.serasa.api.rest.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Perfil comercial da empresa, na ida e na volta. Todos os campos são opcionais.
 *
 * <p>Mesmo formato para leitura e escrita: a tela edita o formulário inteiro e devolve o que
 * leu, e dois DTOs iguais só serviriam para divergir.</p>
 */
public record DadosComerciaisDto(
        @PositiveOrZero BigDecimal faturamentoMensal,
        @PositiveOrZero BigDecimal ticketMedio,
        @PositiveOrZero BigDecimal valorMedioOperacoes,
        @PositiveOrZero Integer prazoMedioVendas,
        @PositiveOrZero Integer prazoMedioPgtoFornecedor,
        @PositiveOrZero Integer prazoMedioEntrega,
        @PositiveOrZero Integer possuiCanhotosDias,
        Boolean operaMercadoriaEntregue,
        Boolean operaMercadoriaEmbarcada,
        @Size(max = 2000) String regiaoAtendimento,
        @Size(max = 2000) String produtos,
        @Size(max = 2000) String parceirosAtuais,
        @Size(max = 4000) String outros) {
}
