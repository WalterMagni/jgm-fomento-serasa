package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class EstagioProspeccaoTest {

    @Test
    @DisplayName("caminho feliz: triagem até pronto para habilitação")
    void shouldAllowHappyPath() {
        assertThat(EstagioProspeccao.TRIAGEM.aceita(EstagioProspeccao.EM_ANALISE)).isTrue();
        assertThat(EstagioProspeccao.EM_ANALISE.aceita(EstagioProspeccao.APROVADO)).isTrue();
        assertThat(EstagioProspeccao.APROVADO.aceita(EstagioProspeccao.DOCS_PENDENTES)).isTrue();
        assertThat(EstagioProspeccao.DOCS_PENDENTES.aceita(EstagioProspeccao.DOCS_COMPLETOS)).isTrue();
        assertThat(EstagioProspeccao.DOCS_COMPLETOS.aceita(EstagioProspeccao.PRONTO_HABILITACAO)).isTrue();
    }

    @Test
    @DisplayName("não dá para pular a análise nem a coleta de documentos")
    void shouldRejectSkippingStages() {
        assertThat(EstagioProspeccao.TRIAGEM.aceita(EstagioProspeccao.APROVADO)).isFalse();
        assertThat(EstagioProspeccao.TRIAGEM.aceita(EstagioProspeccao.DOCS_COMPLETOS)).isFalse();
        assertThat(EstagioProspeccao.EM_ANALISE.aceita(EstagioProspeccao.DOCS_PENDENTES)).isFalse();
        assertThat(EstagioProspeccao.APROVADO.aceita(EstagioProspeccao.PRONTO_HABILITACAO)).isFalse();
    }

    @Test
    @DisplayName("retrocesso declarado corrige engano de operação sem exigir card novo")
    void shouldAllowDeclaredBacksteps() {
        assertThat(EstagioProspeccao.EM_ANALISE.aceita(EstagioProspeccao.TRIAGEM)).isTrue();
        assertThat(EstagioProspeccao.APROVADO.aceita(EstagioProspeccao.EM_ANALISE)).isTrue();
        assertThat(EstagioProspeccao.DOCS_PENDENTES.aceita(EstagioProspeccao.APROVADO)).isTrue();
        assertThat(EstagioProspeccao.DOCS_COMPLETOS.aceita(EstagioProspeccao.DOCS_PENDENTES)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = EstagioProspeccao.class,
            names = {"TRIAGEM", "EM_ANALISE", "APROVADO", "DOCS_PENDENTES", "DOCS_COMPLETOS"})
    @DisplayName("remover do radar é possível de qualquer estágio em aberto")
    void shouldAllowRemovalFromAnyOpenStage(EstagioProspeccao estagio) {
        assertThat(estagio.aceita(EstagioProspeccao.REMOVIDO_RADAR)).isTrue();
    }

    @Test
    @DisplayName("terminais liberam o CNPJ para um card novo")
    void shouldMarkTerminals() {
        assertThat(EstagioProspeccao.REPROVADO.terminal()).isTrue();
        assertThat(EstagioProspeccao.REMOVIDO_RADAR.terminal()).isTrue();
        assertThat(EstagioProspeccao.PRONTO_HABILITACAO.terminal()).isTrue();
        assertThat(EstagioProspeccao.DOCS_PENDENTES.terminal()).isFalse();
    }

    @Test
    @DisplayName("reprovado e removido voltam só para triagem; pronto para habilitação não volta")
    void shouldReopenOnlyToTriage() {
        assertThat(EstagioProspeccao.REPROVADO.destinos()).containsExactly(EstagioProspeccao.TRIAGEM);
        assertThat(EstagioProspeccao.REMOVIDO_RADAR.destinos()).containsExactly(EstagioProspeccao.TRIAGEM);
        assertThat(EstagioProspeccao.PRONTO_HABILITACAO.destinos()).isEmpty();
    }

    @Test
    @DisplayName("só os terminais com desfecho negativo exigem motivo")
    void shouldRequireReasonOnNegativeOutcomes() {
        assertThat(EstagioProspeccao.REPROVADO.exigeMotivo()).isTrue();
        assertThat(EstagioProspeccao.REMOVIDO_RADAR.exigeMotivo()).isTrue();
        assertThat(EstagioProspeccao.PRONTO_HABILITACAO.exigeMotivo()).isFalse();
    }

    @Test
    @DisplayName("SLA corre só nos estágios de trabalho; os defaults são 1/2/10 dias úteis")
    void shouldExposeSlaWindows() {
        assertThat(EstagioProspeccao.TRIAGEM.prazoDiasUteis()).isEqualTo(1);
        assertThat(EstagioProspeccao.EM_ANALISE.prazoDiasUteis()).isEqualTo(2);
        assertThat(EstagioProspeccao.DOCS_PENDENTES.prazoDiasUteis()).isEqualTo(10);
        assertThat(EstagioProspeccao.PRONTO_HABILITACAO.contaSla()).isFalse();
        assertThat(EstagioProspeccao.REPROVADO.contaSla()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(EstagioProspeccao.class)
    @DisplayName("nenhum estágio aponta para si mesmo")
    void shouldNotLoopOnItself(EstagioProspeccao estagio) {
        assertThat(estagio.destinos()).doesNotContain(estagio);
    }
}
