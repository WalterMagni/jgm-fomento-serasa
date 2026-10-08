package com.portal.serasa.domain.model.liberacao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TipoOperacaoTest {

    @Test
    @DisplayName("vazio vira nulo: tipo é opcional")
    void shouldTreatBlankAsNull() {
        assertThat(TipoOperacao.resolver(null, List.of())).isNull();
        assertThat(TipoOperacao.resolver("   ", List.of())).isNull();
    }

    @Test
    @DisplayName("casa com o padrão sem diferenciar maiúscula nem acento")
    void shouldMatchDefaultIgnoringCaseAndAccent() {
        assertThat(TipoOperacao.resolver("comissaria", List.of())).isEqualTo("Comissária");
        assertThat(TipoOperacao.resolver(" DUPLICATA ", List.of())).isEqualTo("Duplicata");
    }

    @Test
    @DisplayName("casa com tipo personalizado que outro card já usa")
    void shouldMatchExistingCustomType() {
        assertThat(TipoOperacao.resolver("cessao  de credito", List.of("Cessão de crédito"))).isEqualTo("Cessão de crédito");
    }

    @Test
    @DisplayName("tipo novo: espaços normalizados e primeira letra maiúscula")
    void shouldNormalizeNewType() {
        assertThat(TipoOperacao.resolver("  nota   promissória ", List.of())).isEqualTo("Nota promissória");
    }

    @Test
    @DisplayName("acima de 40 caracteres é recusado")
    void shouldRejectTooLong() {
        assertThatThrownBy(() -> TipoOperacao.resolver("x".repeat(41), List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
