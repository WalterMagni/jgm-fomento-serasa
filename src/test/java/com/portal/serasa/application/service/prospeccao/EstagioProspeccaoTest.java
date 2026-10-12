package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.portal.serasa.domain.model.prospeccao.EstagioProspeccao.AGUARDANDO_ACEITE;
import static com.portal.serasa.domain.model.prospeccao.EstagioProspeccao.COMITE;
import static com.portal.serasa.domain.model.prospeccao.EstagioProspeccao.CONTRATO;
import static com.portal.serasa.domain.model.prospeccao.EstagioProspeccao.DOCUMENTACAO;
import static com.portal.serasa.domain.model.prospeccao.EstagioProspeccao.EM_ANALISE;
import static com.portal.serasa.domain.model.prospeccao.EstagioProspeccao.HABILITADO;
import static com.portal.serasa.domain.model.prospeccao.EstagioProspeccao.INERTE;
import static com.portal.serasa.domain.model.prospeccao.EstagioProspeccao.NOVA;
import static com.portal.serasa.domain.model.prospeccao.EstagioProspeccao.RECUSADO_CLIENTE;
import static com.portal.serasa.domain.model.prospeccao.EstagioProspeccao.REPROVADO;
import static org.assertj.core.api.Assertions.assertThat;

class EstagioProspeccaoTest {

    @Test
    @DisplayName("caminho feliz: nova até habilitado")
    void shouldAllowHappyPath() {
        assertThat(NOVA.aceita(EM_ANALISE)).isTrue();
        assertThat(EM_ANALISE.aceita(AGUARDANDO_ACEITE)).isTrue();
        assertThat(AGUARDANDO_ACEITE.aceita(DOCUMENTACAO)).isTrue();
        assertThat(DOCUMENTACAO.aceita(COMITE)).isTrue();
        assertThat(COMITE.aceita(CONTRATO)).isTrue();
        assertThat(CONTRATO.aceita(HABILITADO)).isTrue();
    }

    @Test
    @DisplayName("não dá para pular decisão, aceite, documentação nem comitê")
    void shouldRejectSkippingStages() {
        assertThat(NOVA.aceita(AGUARDANDO_ACEITE)).isFalse();
        assertThat(EM_ANALISE.aceita(DOCUMENTACAO)).isFalse();
        assertThat(AGUARDANDO_ACEITE.aceita(COMITE)).isFalse();
        assertThat(DOCUMENTACAO.aceita(CONTRATO)).isFalse();
        assertThat(COMITE.aceita(HABILITADO)).isFalse();
    }

    @Test
    @DisplayName("retrocessos declarados: devolver, contraproposta, comitê devolve, contrato volta")
    void shouldAllowDeclaredBacksteps() {
        assertThat(EM_ANALISE.aceita(NOVA)).isTrue();
        assertThat(AGUARDANDO_ACEITE.aceita(EM_ANALISE)).isTrue();
        assertThat(DOCUMENTACAO.aceita(EM_ANALISE)).isTrue();
        assertThat(COMITE.aceita(EM_ANALISE)).isTrue();
        assertThat(CONTRATO.aceita(COMITE)).isTrue();
    }

    @Test
    @DisplayName("saídas negativas: reprovar na análise ou no comitê, recusa só no aceite")
    void shouldExposeNegativeExits() {
        assertThat(EM_ANALISE.aceita(REPROVADO)).isTrue();
        assertThat(COMITE.aceita(REPROVADO)).isTrue();
        assertThat(AGUARDANDO_ACEITE.aceita(RECUSADO_CLIENTE)).isTrue();
        assertThat(DOCUMENTACAO.aceita(REPROVADO)).isFalse();
        assertThat(EM_ANALISE.aceita(RECUSADO_CLIENTE)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = EstagioProspeccao.class, names = {"NOVA", "EM_ANALISE", "AGUARDANDO_ACEITE", "DOCUMENTACAO"})
    @DisplayName("inerte é possível enquanto o cliente ainda pode sumir")
    void shouldAllowInertWhileClientCanVanish(EstagioProspeccao estagio) {
        assertThat(estagio.aceita(INERTE)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = EstagioProspeccao.class, names = {"HABILITADO", "REPROVADO", "RECUSADO_CLIENTE", "INERTE"})
    @DisplayName("terminais liberam o CNPJ e só voltam por reabertura")
    void shouldMarkTerminals(EstagioProspeccao estagio) {
        assertThat(estagio.terminal()).isTrue();
        assertThat(estagio.destinos()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = EstagioProspeccao.class, names = {"NOVA", "EM_ANALISE", "AGUARDANDO_ACEITE", "DOCUMENTACAO", "COMITE", "CONTRATO"})
    @DisplayName("estágios de trabalho não são terminais")
    void shouldNotMarkWorkStagesAsTerminal(EstagioProspeccao estagio) {
        assertThat(estagio.terminal()).isFalse();
    }

    @Test
    @DisplayName("só reprovar e inerte exigem motivo")
    void shouldRequireReasonOnNegativeOutcomes() {
        assertThat(REPROVADO.exigeMotivo()).isTrue();
        assertThat(INERTE.exigeMotivo()).isTrue();
        assertThat(RECUSADO_CLIENTE.exigeMotivo()).isFalse();
        assertThat(HABILITADO.exigeMotivo()).isFalse();
    }

    @Test
    @DisplayName("prazos em horas úteis, conforme o time pratica: 12 para pegar, 24 para decidir")
    void shouldExposeSlaWindows() {
        assertThat(NOVA.prazoHorasUteis()).isEqualTo(12);
        assertThat(EM_ANALISE.prazoHorasUteis()).isEqualTo(24);
        assertThat(DOCUMENTACAO.contaSla()).isFalse();
        assertThat(REPROVADO.contaSla()).isFalse();
    }

    @Test
    @DisplayName("silêncio corre onde quem deve resposta é o cliente: aceite e documentação")
    void shouldCountSilenceWhereClientOwesAnswer() {
        assertThat(AGUARDANDO_ACEITE.contaSilencio()).isTrue();
        assertThat(DOCUMENTACAO.contaSilencio()).isTrue();
        assertThat(EM_ANALISE.contaSilencio()).isFalse();
        assertThat(COMITE.contaSilencio()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(EstagioProspeccao.class)
    @DisplayName("nenhum estágio aponta para si mesmo")
    void shouldNotLoopOnItself(EstagioProspeccao estagio) {
        assertThat(estagio.destinos()).doesNotContain(estagio);
    }

    @Test
    @DisplayName("sair da análise e do aceite exige a ação própria; o resto é troca simples")
    void shouldFlagDedicatedActions() {
        assertThat(EM_ANALISE.acaoPropria(AGUARDANDO_ACEITE)).contains("decisão");
        assertThat(EM_ANALISE.acaoPropria(REPROVADO)).contains("decisão");
        assertThat(AGUARDANDO_ACEITE.acaoPropria(DOCUMENTACAO)).contains("cliente");
        assertThat(AGUARDANDO_ACEITE.acaoPropria(RECUSADO_CLIENTE)).contains("cliente");
        assertThat(AGUARDANDO_ACEITE.acaoPropria(EM_ANALISE)).contains("cliente");
        assertThat(EM_ANALISE.acaoPropria(NOVA)).isNull();
        assertThat(EM_ANALISE.acaoPropria(INERTE)).isNull();
        assertThat(DOCUMENTACAO.acaoPropria(COMITE)).isNull();
    }
}
