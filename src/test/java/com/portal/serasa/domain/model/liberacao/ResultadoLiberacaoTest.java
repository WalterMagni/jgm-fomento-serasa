package com.portal.serasa.domain.model.liberacao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static com.portal.serasa.domain.model.liberacao.ResultadoLiberacao.APROVADO;
import static com.portal.serasa.domain.model.liberacao.ResultadoLiberacao.PARCIAL;
import static com.portal.serasa.domain.model.liberacao.ResultadoLiberacao.REPROVADO;
import static org.assertj.core.api.Assertions.assertThat;

class ResultadoLiberacaoTest {

    @Test
    @DisplayName("doCard: todos aprovados = APROVADO")
    void shouldBeApprovedWhenAllApproved() {
        assertThat(ResultadoLiberacao.doCard(List.of(APROVADO, APROVADO, APROVADO))).isEqualTo(APROVADO);
    }

    @Test
    @DisplayName("doCard: todos reprovados = REPROVADO")
    void shouldBeRejectedWhenAllRejected() {
        assertThat(ResultadoLiberacao.doCard(List.of(REPROVADO, REPROVADO))).isEqualTo(REPROVADO);
    }

    @Test
    @DisplayName("doCard: aprovado e reprovado misturados = PARCIAL")
    void shouldBePartialWhenMixed() {
        assertThat(ResultadoLiberacao.doCard(List.of(APROVADO, REPROVADO))).isEqualTo(PARCIAL);
        assertThat(ResultadoLiberacao.doCard(List.of(REPROVADO, APROVADO, APROVADO))).isEqualTo(PARCIAL);
    }

    @Test
    @DisplayName("doCard: qualquer sacado parcial faz o card ser PARCIAL, mesmo com todos os outros iguais")
    void shouldBePartialWhenAnySacadoIsPartial() {
        assertThat(ResultadoLiberacao.doCard(List.of(PARCIAL))).isEqualTo(PARCIAL);
        assertThat(ResultadoLiberacao.doCard(List.of(APROVADO, APROVADO, PARCIAL))).isEqualTo(PARCIAL);
        assertThat(ResultadoLiberacao.doCard(List.of(REPROVADO, PARCIAL))).isEqualTo(PARCIAL);
    }

    @Test
    @DisplayName("doCard: um único sacado devolve a própria situação")
    void shouldReturnTheSingleSituation() {
        assertThat(ResultadoLiberacao.doCard(List.of(APROVADO))).isEqualTo(APROVADO);
        assertThat(ResultadoLiberacao.doCard(List.of(REPROVADO))).isEqualTo(REPROVADO);
    }

    @Test
    @DisplayName("doCard: sem decisão nenhuma o resultado é nulo")
    void shouldBeNullWhenThereIsNothingToDecide() {
        assertThat(ResultadoLiberacao.doCard(List.of())).isNull();
    }

    @ParameterizedTest
    @EnumSource(ResultadoLiberacao.class)
    @DisplayName("doCard: repetir o mesmo valor não muda o resultado")
    void shouldBeStableWhenSituationRepeats(ResultadoLiberacao situacao) {
        assertThat(ResultadoLiberacao.doCard(List.of(situacao, situacao, situacao))).isEqualTo(situacao);
    }

    @Test
    @DisplayName("rotulo(): texto em português para tela, histórico e planilha")
    void shouldLabelInPortuguese() {
        assertThat(APROVADO.rotulo()).isEqualTo("Aprovado");
        assertThat(REPROVADO.rotulo()).isEqualTo("Reprovado");
        assertThat(PARCIAL.rotulo()).isEqualTo("Parcialmente aprovado");
    }
}
