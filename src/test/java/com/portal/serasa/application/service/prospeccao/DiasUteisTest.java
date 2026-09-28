package com.portal.serasa.application.service.prospeccao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DiasUteisTest {

    @ParameterizedTest
    @CsvSource({
            "2024, 2024-03-31",
            "2025, 2025-04-20",
            "2026, 2026-04-05",
            "2027, 2027-03-28"
    })
    @DisplayName("pascoa: datas conferidas contra o calendário")
    void shouldComputeEaster(int ano, String esperado) {
        assertThat(DiasUteis.pascoa(ano)).isEqualTo(LocalDate.parse(esperado));
    }

    @Test
    @DisplayName("entre: não conta o dia inicial — entrar no estágio hoje é zero dia decorrido")
    void shouldNotCountStartDay() {
        LocalDate segunda = LocalDate.of(2026, 9, 21);
        assertThat(DiasUteis.entre(segunda, segunda)).isZero();
        assertThat(DiasUteis.entre(segunda, segunda.plusDays(1))).isEqualTo(1);
    }

    @Test
    @DisplayName("entre: pula o fim de semana")
    void shouldSkipWeekend() {
        LocalDate sexta = LocalDate.of(2026, 9, 18);
        LocalDate segunda = LocalDate.of(2026, 9, 21);
        assertThat(DiasUteis.entre(sexta, segunda)).isEqualTo(1);
    }

    @Test
    @DisplayName("entre: pula feriado nacional fixo — 7 de setembro de 2026 cai numa segunda")
    void shouldSkipFixedHoliday() {
        LocalDate sexta = LocalDate.of(2026, 9, 4);
        LocalDate terca = LocalDate.of(2026, 9, 8);
        // sábado, domingo e a segunda do feriado não contam: sobra só a terça.
        assertThat(DiasUteis.entre(sexta, terca)).isEqualTo(1);
    }

    @Test
    @DisplayName("entre: pula feriado móvel — carnaval de 2026 é 16 e 17 de fevereiro")
    void shouldSkipCarnival() {
        LocalDate sexta = LocalDate.of(2026, 2, 13);
        LocalDate sextaSeguinte = LocalDate.of(2026, 2, 20);
        // 5 dias úteis na semana, menos as duas do carnaval.
        assertThat(DiasUteis.entre(sexta, sextaSeguinte)).isEqualTo(3);
    }

    @Test
    @DisplayName("entre: atravessa o ano sem perder os feriados de nenhum dos dois")
    void shouldHandleYearBoundary() {
        LocalDate dez = LocalDate.of(2025, 12, 24);
        LocalDate jan = LocalDate.of(2026, 1, 2);
        // Fora 25/12 e 01/01 (feriados) e 27 e 28 (fim de semana), sobram 26, 29, 30, 31 e 02.
        assertThat(DiasUteis.entre(dez, jan)).isEqualTo(5);
    }

    @Test
    @DisplayName("entre: data final anterior à inicial não é negativa")
    void shouldClampInvertedRange() {
        assertThat(DiasUteis.entre(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 1))).isZero();
    }
}
