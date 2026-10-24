package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.portal.serasa.domain.model.prospeccao.EstagioProspeccao.AGUARDANDO_ACEITE;
import static com.portal.serasa.domain.model.prospeccao.EstagioProspeccao.APROVADO;
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
        assertThat(EM_ANALISE.aceita(APROVADO)).isTrue();
        assertThat(APROVADO.aceita(DOCUMENTACAO)).isTrue();
        assertThat(DOCUMENTACAO.aceita(COMITE)).isTrue();
        assertThat(COMITE.aceita(AGUARDANDO_ACEITE)).isTrue();
        assertThat(AGUARDANDO_ACEITE.aceita(CONTRATO)).isTrue();
        assertThat(CONTRATO.aceita(HABILITADO)).isTrue();
    }

    @Test
    @DisplayName("não dá para pular decisão, documentação, comitê nem aceite")
    void shouldRejectSkippingStages() {
        assertThat(NOVA.aceita(APROVADO)).isFalse();
        assertThat(EM_ANALISE.aceita(DOCUMENTACAO)).isFalse();
        assertThat(APROVADO.aceita(COMITE)).isFalse();
        assertThat(DOCUMENTACAO.aceita(AGUARDANDO_ACEITE)).isFalse();
        assertThat(COMITE.aceita(CONTRATO)).isFalse();
        assertThat(AGUARDANDO_ACEITE.aceita(HABILITADO)).isFalse();
    }

    @Test
    @DisplayName("retrocessos declarados: devolver, documentação ao comercial, contraproposta ao comitê, comitê devolve, contrato volta")
    void shouldAllowDeclaredBacksteps() {
        assertThat(EM_ANALISE.aceita(NOVA)).isTrue();
        assertThat(APROVADO.aceita(EM_ANALISE)).isTrue();
        assertThat(DOCUMENTACAO.aceita(APROVADO)).isTrue();
        assertThat(AGUARDANDO_ACEITE.aceita(COMITE)).isTrue();
        assertThat(DOCUMENTACAO.aceita(EM_ANALISE)).isTrue();
        assertThat(COMITE.aceita(EM_ANALISE)).isTrue();
        assertThat(CONTRATO.aceita(COMITE)).isTrue();
    }

    @Test
    @DisplayName("saídas negativas: reprovar na análise, no comitê e, depois da aprovação, pelo cancelamento; recusa só no aceite")
    void shouldExposeNegativeExits() {
        assertThat(EM_ANALISE.aceita(REPROVADO)).isTrue();
        assertThat(COMITE.aceita(REPROVADO)).isTrue();
        assertThat(AGUARDANDO_ACEITE.aceita(RECUSADO_CLIENTE)).isTrue();
        assertThat(NOVA.aceita(REPROVADO)).isFalse();
        assertThat(EM_ANALISE.aceita(RECUSADO_CLIENTE)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = EstagioProspeccao.class, names = {"APROVADO", "DOCUMENTACAO", "COMITE", "AGUARDANDO_ACEITE", "CONTRATO"})
    @DisplayName("do Aprovado ao Contrato o card pode ser cancelado, e cai em Reprovado pela ação própria")
    void shouldAllowCancellingAfterApproval(EstagioProspeccao estagio) {
        assertThat(estagio.cancelavel()).isTrue();
        assertThat(estagio.aceita(REPROVADO)).isTrue();
        // no comitê, arrastar para Reprovado continua apontando o resultado; o cancelamento é ação à parte
        assertThat(estagio.acaoPropria(REPROVADO)).isEqualTo(estagio == COMITE ? "o resultado do comitê" : "a reprovação");
    }

    @ParameterizedTest
    @EnumSource(value = EstagioProspeccao.class, names = {"NOVA", "EM_ANALISE", "HABILITADO", "REPROVADO", "RECUSADO_CLIENTE", "INERTE"})
    @DisplayName("antes da aprovação e nos desfechos não há cancelamento")
    void shouldNotCancelBeforeApprovalOrAfterTheEnd(EstagioProspeccao estagio) {
        assertThat(estagio.cancelavel()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = EstagioProspeccao.class, names = {"NOVA", "EM_ANALISE", "APROVADO", "DOCUMENTACAO", "AGUARDANDO_ACEITE"})
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
    @EnumSource(value = EstagioProspeccao.class, names = {"NOVA", "EM_ANALISE", "APROVADO", "DOCUMENTACAO", "COMITE", "AGUARDANDO_ACEITE", "CONTRATO"})
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
        assertThat(NOVA.prazoHorasUteis()).isEqualTo(24);
        assertThat(EM_ANALISE.prazoHorasUteis()).isEqualTo(24);
        assertThat(DOCUMENTACAO.contaSla()).isFalse();
        assertThat(REPROVADO.contaSla()).isFalse();
    }

    @Test
    @DisplayName("silêncio corre onde quem deve resposta é o cliente: aprovado, documentação e aceite")
    void shouldCountSilenceWhereClientOwesAnswer() {
        assertThat(APROVADO.contaSilencio()).isTrue();
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
        assertThat(EM_ANALISE.acaoPropria(APROVADO)).contains("decisão");
        assertThat(EM_ANALISE.acaoPropria(REPROVADO)).contains("decisão");
        assertThat(AGUARDANDO_ACEITE.acaoPropria(CONTRATO)).contains("cliente");
        assertThat(AGUARDANDO_ACEITE.acaoPropria(RECUSADO_CLIENTE)).contains("cliente");
        // Contraproposta volta ao comitê pela resposta do cliente, não pelo envio comum.
        assertThat(AGUARDANDO_ACEITE.acaoPropria(COMITE)).contains("cliente");
        assertThat(APROVADO.acaoPropria(DOCUMENTACAO)).isNull();
        assertThat(DOCUMENTACAO.acaoPropria(APROVADO)).isNull();
        assertThat(EM_ANALISE.acaoPropria(NOVA)).isNull();
        assertThat(EM_ANALISE.acaoPropria(INERTE)).isNull();
        assertThat(DOCUMENTACAO.acaoPropria(EM_ANALISE)).isNull();
        assertThat(CONTRATO.acaoPropria(COMITE)).contains("comitê");
    }

    @Test
    @DisplayName("comitê e contrato: entrar convoca, sair é resultado, concluir exige contrato")
    void shouldFlagCommitteeActions() {
        assertThat(DOCUMENTACAO.acaoPropria(COMITE)).contains("comitê");
        assertThat(COMITE.acaoPropria(AGUARDANDO_ACEITE)).contains("resultado");
        assertThat(COMITE.acaoPropria(EM_ANALISE)).contains("resultado");
        assertThat(COMITE.acaoPropria(REPROVADO)).contains("resultado");
        assertThat(CONTRATO.acaoPropria(HABILITADO)).contains("contrato");
    }
}
