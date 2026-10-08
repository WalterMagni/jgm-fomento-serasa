package com.portal.serasa.api.rest.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LiberacaoResponseAssemblerTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Walter Magni Marques|WM",
            "Andressa da Silva|AS",
            "Ana Li|AL",
            "Andressa Lima Souza|AS",
            "Aline|AL",
            "Maria de Fátima dos Santos|MS",
            "  Joana   Prado  |JP",
            "walter magni|WM",
            "Zé|ZÉ",
            "A|A"
    })
    @DisplayName("iniciais: primeira e última palavra relevante; partículas minúsculas curtas são ignoradas")
    void shouldBuildInitials(String nome, String esperado) {
        assertThat(LiberacaoResponseAssembler.iniciais(nome)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("iniciais: nome nulo ou em branco vira '?'")
    void shouldReturnQuestionMarkForMissingName(String nome) {
        assertThat(LiberacaoResponseAssembler.iniciais(nome)).isEqualTo("?");
    }

    @Test
    @DisplayName("iniciais: nome só de partículas minúsculas curtas não quebra")
    void shouldNotBreakWithOnlyShortLowercaseParticles() {
        assertThat(LiberacaoResponseAssembler.iniciais("da de")).isEqualTo("DD");
        assertThat(LiberacaoResponseAssembler.iniciais("da")).isEqualTo("DA");
    }
}
