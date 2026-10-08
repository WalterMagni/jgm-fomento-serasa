package com.portal.serasa.domain.model.liberacao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PropostaArTest {

    private static PropostaAr comData(String emitidaEm) {
        return proposta(emitidaEm, new BigDecimal("150030"), new BigDecimal("255.8025"));
    }

    private static PropostaAr proposta(String emitidaEm, BigDecimal limite, BigDecimal comprometimento) {
        return new PropostaAr("1341", emitidaEm, null, limite, BigDecimal.ZERO, 3, new BigDecimal("33"),
                new BigDecimal("24581"), new BigDecimal("867.34"), new BigDecimal("23713.66"), 4, new BigDecimal("27111"),
                null, null, null, null, null, null,
                null, null, null, comprometimento, null, null);
    }

    @Test
    @DisplayName("data válida fica em ISO com segundos e vira LocalDateTime")
    void shouldNormalizeValidDate() {
        PropostaAr proposta = comData("2026-10-08T15:59");

        assertThat(proposta.emitidaEm()).isEqualTo("2026-10-08T15:59:00");
        assertThat(proposta.emitidaEmData()).isEqualTo(LocalDateTime.of(2026, 10, 8, 15, 59));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-31T15:59:13", "abc", "08/10/2026 15:59", "  "})
    @DisplayName("data que não existe ou fora do formato vira nula, sem derrubar quem lê")
    void shouldDropInvalidDate(String emitidaEm) {
        PropostaAr proposta = comData(emitidaEm);

        assertThat(proposta.emitidaEm()).isNull();
        assertThat(proposta.emitidaEmData()).isNull();
    }

    @Test
    @DisplayName("mesmos valores com escala diferente são a mesma proposta")
    void shouldBeEqualRegardlessOfScale() {
        PropostaAr semCasas = proposta("2026-10-08T15:59:13", new BigDecimal("150030"), new BigDecimal("255.8025"));
        PropostaAr comCasas = proposta("2026-10-08T15:59:13", new BigDecimal("150030.00"), new BigDecimal("255.80250"));

        assertThat(semCasas).isEqualTo(comCasas);
        assertThat(semCasas.limiteIndividual()).isEqualTo(new BigDecimal("150030.00"));
        assertThat(semCasas.comprometimentoApos()).isEqualTo(new BigDecimal("255.8025"));
        assertThat(semCasas.prazoMedio()).isEqualTo(new BigDecimal("33.00"));
    }

    @Test
    @DisplayName("carteira do sacado: mesma regra de escala")
    void shouldNormalizeCarteiraScale() {
        CarteiraSacado a = new CarteiraSacado(1, new BigDecimal("0"), new BigDecimal("5077"), new BigDecimal("5077.0"),
                new BigDecimal("32705.51"), null);
        CarteiraSacado b = new CarteiraSacado(1, new BigDecimal("0.00"), new BigDecimal("5077.00"), new BigDecimal("5077.00"),
                new BigDecimal("32705.510"), null);

        assertThat(a).isEqualTo(b);
        assertThat(a.recomprados()).isNull();
    }
}
