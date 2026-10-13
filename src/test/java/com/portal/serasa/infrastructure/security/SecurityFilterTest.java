package com.portal.serasa.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Login emitido antes da troca de senha deixa de valer. */
class SecurityFilterTest {

    private static final Instant TROCA = Instant.parse("2026-10-12T15:00:00.700Z");

    @Test
    @DisplayName("quem nunca trocou a senha não perde a sessão")
    void semTroca() {
        assertThat(SecurityFilter.emitidoAntesDaTroca(Instant.parse("2026-10-01T10:00:00Z"), null)).isFalse();
        assertThat(SecurityFilter.emitidoAntesDaTroca(null, null)).isFalse();
    }

    @Test
    @DisplayName("token anterior à troca, ou sem data de emissão, é recusado")
    void anteriorATroca() {
        assertThat(SecurityFilter.emitidoAntesDaTroca(Instant.parse("2026-10-12T14:59:59Z"), TROCA)).isTrue();
        assertThat(SecurityFilter.emitidoAntesDaTroca(null, TROCA)).isTrue();
    }

    @Test
    @DisplayName("login feito no mesmo segundo da troca vale: a data do token não tem milissegundos")
    void mesmoSegundo() {
        assertThat(SecurityFilter.emitidoAntesDaTroca(Instant.parse("2026-10-12T15:00:00Z"), TROCA)).isFalse();
        assertThat(SecurityFilter.emitidoAntesDaTroca(Instant.parse("2026-10-12T15:00:05Z"), TROCA)).isFalse();
    }
}
