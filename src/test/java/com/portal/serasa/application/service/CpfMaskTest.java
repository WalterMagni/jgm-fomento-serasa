package com.portal.serasa.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CpfMaskTest {

    /**
     * Pares reais: CPF completo vindo do QSA do Serasa e a mascara correspondente na tabela
     * socios da Receita. Se essa regra mudar, o casamento entre as duas fontes para de
     * funcionar em silencio — por isso o teste fixa exemplos concretos.
     */
    @ParameterizedTest
    @CsvSource({
            "04321650887, ***216508**",
            "87349264887, ***492648**",
            "30783169809, ***831698**",
            "00612812987, ***128129**"
    })
    @DisplayName("fromCpf: extrai os digitos 4..9 no formato publicado pela Receita")
    void shouldDeriveReceitaMaskFromFullCpf(String cpf, String expectedMask) {
        assertThat(CpfMask.fromCpf(cpf)).isEqualTo(expectedMask);
    }

    @Test
    @DisplayName("fromCpf: aceita CPF formatado")
    void shouldAcceptFormattedCpf() {
        assertThat(CpfMask.fromCpf("043.216.508-87")).isEqualTo("***216508**");
    }

    @Test
    @DisplayName("fromCpf: retorna null para valor que nao e CPF de 11 digitos")
    void shouldReturnNullForNonCpf() {
        assertThat(CpfMask.fromCpf(null)).isNull();
        assertThat(CpfMask.fromCpf("")).isNull();
        assertThat(CpfMask.fromCpf("123")).isNull();
        assertThat(CpfMask.fromCpf("34438918000139")).isNull(); // CNPJ de socio PJ
    }

    @Test
    @DisplayName("isMask: reconhece o formato da Receita")
    void shouldRecognizeMaskFormat() {
        assertThat(CpfMask.isMask("***216508**")).isTrue();
        assertThat(CpfMask.isMask("04321650887")).isFalse();
        assertThat(CpfMask.isMask(null)).isFalse();
    }
}
