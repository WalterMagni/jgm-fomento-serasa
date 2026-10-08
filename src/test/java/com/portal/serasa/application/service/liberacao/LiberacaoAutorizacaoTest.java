package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.domain.exception.TransicaoInvalidaException;
import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoCardEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoPendenciaEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiberacaoAutorizacaoTest {

    private final LiberacaoAutorizacao autorizacao = new LiberacaoAutorizacao();

    private final UserEntity comum = UserEntity.builder().id(UUID.randomUUID()).name("Auxiliar").build();
    private final UserEntity analista = UserEntity.builder().id(UUID.randomUUID()).name("Analista").analista(true).build();

    private LiberacaoCardEntity card(EtapaLiberacao etapa) {
        return LiberacaoCardEntity.builder().id(UUID.randomUUID()).etapa(etapa).rodada(1).build();
    }

    private LiberacaoPendenciaEntity pendenciaPara(UUID destinatarioId) {
        return LiberacaoPendenciaEntity.builder()
                .id(UUID.randomUUID())
                .destinatarioId(destinatarioId)
                .destinatarioNome("Carla")
                .build();
    }

    // ------------------------------------------------------------------ edição

    @Test
    @DisplayName("qualquer usuário edita card na Origem")
    void shouldLetAnyoneEditOnOrigem() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);

        assertThat(autorizacao.podeEditar(card, comum)).isTrue();
        assertThatCode(() -> autorizacao.exigirEdicao(card, comum)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"COMITE", "PENDENCIA", "FINALIZADO"})
    @DisplayName("não-analista não edita card do Comitê em diante")
    void shouldDenyEditByNonAnalystFromComiteOn(EtapaLiberacao etapa) {
        LiberacaoCardEntity card = card(etapa);

        assertThat(autorizacao.podeEditar(card, comum)).isFalse();
        assertThatThrownBy(() -> autorizacao.exigirEdicao(card, comum))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessage("A partir do Comitê, só analista altera o card.");
    }

    @ParameterizedTest
    @EnumSource(EtapaLiberacao.class)
    @DisplayName("analista edita em qualquer etapa")
    void shouldLetAnalystEditAnywhere(EtapaLiberacao etapa) {
        LiberacaoCardEntity card = card(etapa);

        assertThat(autorizacao.podeEditar(card, analista)).isTrue();
        assertThatCode(() -> autorizacao.exigirEdicao(card, analista)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sem usuário autenticado nada passa")
    void shouldRequireAuthentication() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        UserEntity semId = UserEntity.builder().name("Fantasma").build();

        assertThatThrownBy(() -> autorizacao.exigirEdicao(card, null)).isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> autorizacao.exigirEdicao(card, semId)).isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> autorizacao.exigirTransicao(card, EtapaLiberacao.COMITE, null, 0, false))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> autorizacao.exigirAnalista(null, "abrir pendência"))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> autorizacao.exigirResposta(pendenciaPara(UUID.randomUUID()), null))
                .isInstanceOf(AcessoNegadoException.class);
    }

    // ------------------------------------------------------------- transições

    @Test
    @DisplayName("qualquer usuário move Origem→Comitê")
    void shouldLetAnyoneMoveOrigemToComite() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);

        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.COMITE, comum, 0, false)).isEmpty();
        assertThatCode(() -> autorizacao.exigirTransicao(card, EtapaLiberacao.COMITE, comum, 0, false))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"COMITE", "PENDENCIA", "FINALIZADO"})
    @DisplayName("não-analista não move card do Comitê em diante, para nenhum destino válido")
    void shouldDenyMoveByNonAnalystFromComiteOn(EtapaLiberacao origem) {
        LiberacaoCardEntity card = card(origem);

        for (EtapaLiberacao destino : origem.destinos()) {
            assertThat(autorizacao.motivoBloqueio(card, destino, comum, 2, false))
                    .contains("A partir do Comitê, só analista move o card.");
            assertThatThrownBy(() -> autorizacao.exigirTransicao(card, destino, comum, 2, false))
                    .isInstanceOf(AcessoNegadoException.class)
                    .hasMessage("A partir do Comitê, só analista move o card.");
        }
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"COMITE", "PENDENCIA", "FINALIZADO"})
    @DisplayName("analista move card do Comitê em diante por qualquer caminho válido (com parecer registrado)")
    void shouldLetAnalystMoveFromComiteOn(EtapaLiberacao origem) {
        LiberacaoCardEntity card = card(origem);

        for (EtapaLiberacao destino : origem.destinos()) {
            assertThat(autorizacao.motivoBloqueio(card, destino, analista, 1, false)).isEmpty();
            assertThatCode(() -> autorizacao.exigirTransicao(card, destino, analista, 1, false))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("caminho inválido: motivo 'Não dá para ir de X para Y.'")
    void shouldExplainInvalidPath() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);

        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.FINALIZADO, analista, 0, false))
                .hasValueSatisfying(motivo -> assertThat(motivo)
                        .startsWith("Não dá para ir")
                        .isEqualTo("Não dá para ir de Origem para Finalizados."));
        assertThat(autorizacao.motivoBloqueio(card(EtapaLiberacao.FINALIZADO), EtapaLiberacao.PENDENCIA, analista, 0, false))
                .contains("Não dá para ir de Finalizados para Pendência.");
    }

    @Test
    @DisplayName("caminho inválido vale também para analista, e o motivo vem antes da regra de papel")
    void shouldCheckPathBeforeRole() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);

        // não-analista em Pendência→Origem: o caminho inválido é o motivo, não o papel
        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.ORIGEM, comum, 0, false))
                .contains("Não dá para ir de Pendência para Origem.");
        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.ORIGEM, analista, 0, false))
                .contains("Não dá para ir de Pendência para Origem.");
    }

    @Test
    @DisplayName("exigirTransicao: caminho inválido é TransicaoInvalidaException (409), não AcessoNegado")
    void shouldThrowInvalidTransitionForInvalidPath() {
        LiberacaoCardEntity card = card(EtapaLiberacao.FINALIZADO);

        assertThatThrownBy(() -> autorizacao.exigirTransicao(card, EtapaLiberacao.ORIGEM, comum, 2, false))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Não dá para ir de Finalizados para Origem.");
    }

    // ------------------------------------------- Comitê: pelo menos um parecer

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"PENDENCIA", "FINALIZADO"})
    @DisplayName("Comitê→Pendência/Finalizado sem nenhum parecer registrado: 'Precisa de pelo menos um parecer do Comitê.'")
    void shouldBlockLeavingComiteWithoutAnyParecer(EtapaLiberacao destino) {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThat(autorizacao.motivoBloqueio(card, destino, analista, 0, false))
                .contains("Precisa de pelo menos um parecer do Comitê.");
        assertThatThrownBy(() -> autorizacao.exigirTransicao(card, destino, analista, 0, false))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Precisa de pelo menos um parecer do Comitê.");
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"PENDENCIA", "FINALIZADO"})
    @DisplayName("Comitê→Pendência/Finalizado com um parecer (de dois esperados) já é liberado")
    void shouldAllowLeavingComiteWithOneParecer(EtapaLiberacao destino) {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        // a contagem vem de quem registrou; quem falta aparece só como aviso()
        assertThat(autorizacao.motivoBloqueio(card, destino, analista, 1, false)).isEmpty();
        assertThatCode(() -> autorizacao.exigirTransicao(card, destino, analista, 1, false))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("com todos os pareceres registrados também libera")
    void shouldAllowLeavingComiteWithAllPareceres() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.FINALIZADO, analista, 5, false)).isEmpty();
    }

    @Test
    @DisplayName("Comitê→Origem sem nenhum parecer é permitido para analista (devolução)")
    void shouldAllowReturnToOrigemWithoutParecer() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.ORIGEM, analista, 0, false)).isEmpty();
        assertThatCode(() -> autorizacao.exigirTransicao(card, EtapaLiberacao.ORIGEM, analista, 0, false))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Pendência→Finalizado não pede parecer: a trava é só na saída do Comitê")
    void shouldNotApplyParecerRuleOutsideComite() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);

        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.FINALIZADO, analista, 0, false)).isEmpty();
        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.COMITE, analista, 0, false)).isEmpty();
    }

    @Test
    @DisplayName("reabrir Finalizado→Comitê não pede parecer (a rodada nova começa vazia)")
    void shouldNotRequireParecerToReopen() {
        LiberacaoCardEntity card = card(EtapaLiberacao.FINALIZADO);

        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.COMITE, analista, 0, false)).isEmpty();
    }

    // --------------------------------------------------------------- aviso

    @Test
    @DisplayName("aviso: uma pessoa faltando usa o singular")
    void shouldWarnInSingularWhenOneParecerIsMissing() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThat(autorizacao.aviso(card, EtapaLiberacao.FINALIZADO, List.of("Mychelly")))
                .contains("Mychelly ainda não deu parecer.");
    }

    @Test
    @DisplayName("aviso: duas pessoas faltando usam o plural, unidas por ' e '")
    void shouldWarnInPluralWhenTwoPareceresAreMissing() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThat(autorizacao.aviso(card, EtapaLiberacao.PENDENCIA, List.of("Mychelly", "Carla")))
                .contains("Mychelly e Carla ainda não deram parecer.");
    }

    @Test
    @DisplayName("aviso: três pessoas faltando: 'A, B e C ainda não deram parecer.'")
    void shouldWarnWithOxfordFreeListForThree() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThat(autorizacao.aviso(card, EtapaLiberacao.FINALIZADO, List.of("Ana", "Bia", "Carla")))
                .contains("Ana, Bia e Carla ainda não deram parecer.");
    }

    @Test
    @DisplayName("aviso: ninguém faltando = sem aviso")
    void shouldNotWarnWhenNobodyIsMissing() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThat(autorizacao.aviso(card, EtapaLiberacao.FINALIZADO, List.of())).isEmpty();
    }

    @Test
    @DisplayName("aviso: devolver à Origem não avisa, mesmo com parecer faltando")
    void shouldNotWarnOnReturnToOrigem() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThat(autorizacao.aviso(card, EtapaLiberacao.ORIGEM, List.of("Mychelly"))).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"ORIGEM", "PENDENCIA", "FINALIZADO"})
    @DisplayName("aviso: só vale saindo do Comitê")
    void shouldNotWarnWhenNotLeavingComite(EtapaLiberacao etapa) {
        LiberacaoCardEntity card = card(etapa);

        for (EtapaLiberacao destino : etapa.destinos()) {
            assertThat(autorizacao.aviso(card, destino, List.of("Mychelly"))).isEmpty();
        }
    }

    @Test
    @DisplayName("juntar: um nome, dois nomes, três nomes e lista vazia")
    void shouldJoinNames() {
        assertThat(LiberacaoAutorizacao.juntar(List.of())).isEmpty();
        assertThat(LiberacaoAutorizacao.juntar(List.of("A"))).isEqualTo("A");
        assertThat(LiberacaoAutorizacao.juntar(List.of("A", "B"))).isEqualTo("A e B");
        assertThat(LiberacaoAutorizacao.juntar(List.of("A", "B", "C"))).isEqualTo("A, B e C");
    }

    // ---------------------------------------------------------- comitê vazio

    @Test
    @DisplayName("→Comitê sem ninguém marcado como Comitê: TransicaoInvalidaException")
    void shouldBlockMoveToComiteWhenComiteIsEmpty() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);

        assertThatThrownBy(() -> autorizacao.exigirTransicao(card, EtapaLiberacao.COMITE, comum, 0, true))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("Ninguém está marcado como Comitê");
    }

    @Test
    @DisplayName("comiteVazio só importa quando o destino é o Comitê")
    void shouldIgnoreEmptyComiteForOtherDestinations() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.ORIGEM, analista, 0, true)).isEmpty();
    }

    @Test
    @DisplayName("motivoBloqueio devolve o motivo de permissão antes do de regra")
    void shouldReturnPermissionMotiveBeforeRuleMotive() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        // não-analista num card sem nenhum parecer: a recusa é de permissão
        Optional<String> motivo = autorizacao.motivoBloqueio(card, EtapaLiberacao.FINALIZADO, comum, 0, false);

        assertThat(motivo).contains("A partir do Comitê, só analista move o card.");
    }

    @Test
    @DisplayName("exigirTransicao: permissão vira AcessoNegado mesmo havendo regra violada")
    void shouldThrowAccessDeniedBeforeInvalidTransition() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThatThrownBy(() -> autorizacao.exigirTransicao(card, EtapaLiberacao.FINALIZADO, comum, 0, false))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    @DisplayName("motivo de permissão também precede 'comitê vazio'")
    void shouldReturnPermissionMotiveBeforeEmptyComiteRule() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);

        // Pendência→Comitê por não-analista: papel recusa antes de a regra do comitê vazio ser avaliada
        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.COMITE, comum, 0, true))
                .contains("A partir do Comitê, só analista move o card.");
    }

    @Test
    @DisplayName("reabrir Finalizado→Comitê com o Comitê vazio é recusado")
    void shouldBlockReopenWhenComiteIsEmpty() {
        LiberacaoCardEntity card = card(EtapaLiberacao.FINALIZADO);

        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.COMITE, analista, 0, true))
                .contains("Ninguém está marcado como Comitê. Peça ao admin para marcar em Configurações.");
    }

    // ---------------------------------------------------------------- analista

    @Test
    @DisplayName("exigirAnalista: não-analista recebe 'Só analista pode <ação>.'")
    void shouldRequireAnalystForAction() {
        assertThatThrownBy(() -> autorizacao.exigirAnalista(comum, "abrir pendência"))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessage("Só analista pode abrir pendência.");
        assertThatCode(() -> autorizacao.exigirAnalista(analista, "abrir pendência")).doesNotThrowAnyException();
    }

    // --------------------------------------------------------------- resposta

    @Test
    @DisplayName("exigirResposta: destinatário não-analista responde")
    void shouldLetRecipientAnswer() {
        LiberacaoPendenciaEntity pendencia = pendenciaPara(comum.getId());

        assertThatCode(() -> autorizacao.exigirResposta(pendencia, comum)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("exigirResposta: outro não-analista é recusado, citando o destinatário")
    void shouldDenyAnswerByStranger() {
        LiberacaoPendenciaEntity pendencia = pendenciaPara(UUID.randomUUID());

        assertThatThrownBy(() -> autorizacao.exigirResposta(pendencia, comum))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessage("Só Carla ou uma analista responde esta pendência.");
    }

    @Test
    @DisplayName("exigirResposta: analista cobre a colega")
    void shouldLetAnalystAnswerForSomeoneElse() {
        LiberacaoPendenciaEntity pendencia = pendenciaPara(UUID.randomUUID());

        assertThatCode(() -> autorizacao.exigirResposta(pendencia, analista)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("exigirResposta: pendência sem destinatário (removido) só analista responde")
    void shouldOnlyLetAnalystAnswerWhenRecipientWasRemoved() {
        LiberacaoPendenciaEntity pendencia = pendenciaPara(null);

        assertThatThrownBy(() -> autorizacao.exigirResposta(pendencia, comum))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatCode(() -> autorizacao.exigirResposta(pendencia, analista)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rótulos das etapas em português")
    void shouldLabelEtapasInPortuguese() {
        assertThat(LiberacaoAutorizacao.rotulo(EtapaLiberacao.ORIGEM)).isEqualTo("Origem");
        assertThat(LiberacaoAutorizacao.rotulo(EtapaLiberacao.COMITE)).isEqualTo("Comitê");
        assertThat(LiberacaoAutorizacao.rotulo(EtapaLiberacao.PENDENCIA)).isEqualTo("Pendência");
        assertThat(LiberacaoAutorizacao.rotulo(EtapaLiberacao.FINALIZADO)).isEqualTo("Finalizados");
    }
}
