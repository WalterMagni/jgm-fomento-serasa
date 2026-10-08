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
    @EnumSource(value = EtapaLiberacao.class, names = {"COMITE", "PENDENCIA", "APROVADO", "REPROVADO"})
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
        assertThatThrownBy(() -> autorizacao.exigirTransicao(card, EtapaLiberacao.COMITE, null, List.of(), false))
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

        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.COMITE, comum, List.of(), false)).isEmpty();
        assertThatCode(() -> autorizacao.exigirTransicao(card, EtapaLiberacao.COMITE, comum, List.of(), false))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"COMITE", "PENDENCIA", "APROVADO", "REPROVADO"})
    @DisplayName("não-analista não move card do Comitê em diante, para nenhum destino válido")
    void shouldDenyMoveByNonAnalystFromComiteOn(EtapaLiberacao origem) {
        LiberacaoCardEntity card = card(origem);

        for (EtapaLiberacao destino : origem.destinos()) {
            assertThat(autorizacao.motivoBloqueio(card, destino, comum, List.of(), false))
                    .contains("A partir do Comitê, só analista move o card.");
            assertThatThrownBy(() -> autorizacao.exigirTransicao(card, destino, comum, List.of(), false))
                    .isInstanceOf(AcessoNegadoException.class)
                    .hasMessage("A partir do Comitê, só analista move o card.");
        }
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"COMITE", "PENDENCIA", "APROVADO", "REPROVADO"})
    @DisplayName("analista move card do Comitê em diante por qualquer caminho válido")
    void shouldLetAnalystMoveFromComiteOn(EtapaLiberacao origem) {
        LiberacaoCardEntity card = card(origem);

        for (EtapaLiberacao destino : origem.destinos()) {
            assertThat(autorizacao.motivoBloqueio(card, destino, analista, List.of(), false)).isEmpty();
            assertThatCode(() -> autorizacao.exigirTransicao(card, destino, analista, List.of(), false))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("caminho inválido: motivo 'Não dá para ir de X para Y.'")
    void shouldExplainInvalidPath() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);

        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.APROVADO, analista, List.of(), false))
                .hasValueSatisfying(motivo -> assertThat(motivo)
                        .startsWith("Não dá para ir")
                        .isEqualTo("Não dá para ir de Origem para Aprovado."));
        assertThat(autorizacao.motivoBloqueio(card(EtapaLiberacao.APROVADO), EtapaLiberacao.PENDENCIA, analista, List.of(), false))
                .contains("Não dá para ir de Aprovado para Pendência.");
    }

    @Test
    @DisplayName("caminho inválido vale também para analista, e o motivo vem antes da regra de papel")
    void shouldCheckPathBeforeRole() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);

        // não-analista em Pendência→Origem: o caminho inválido é o motivo, não o papel
        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.ORIGEM, comum, List.of(), false))
                .contains("Não dá para ir de Pendência para Origem.");
        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.ORIGEM, analista, List.of(), false))
                .contains("Não dá para ir de Pendência para Origem.");
    }

    @Test
    @DisplayName("Comitê→Aprovado com parecer faltando: TransicaoInvalidaException 'Aguardando parecer de Mychelly.'")
    void shouldBlockDecisionWhileWaitingForParecer() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThatThrownBy(() -> autorizacao.exigirTransicao(card, EtapaLiberacao.APROVADO, analista,
                List.of("Mychelly"), false))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Aguardando parecer de Mychelly.");
    }

    @Test
    @DisplayName("vários pareceres faltando: nomes unidos por ' e '")
    void shouldJoinNamesOfPendingMembers() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.REPROVADO, analista,
                List.of("Mychelly", "Carla"), false))
                .contains("Aguardando parecer de Mychelly e Carla.");
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"PENDENCIA", "APROVADO", "REPROVADO"})
    @DisplayName("toda saída de decisão do Comitê espera os pareceres")
    void shouldBlockEveryComiteDecisionWhileWaiting(EtapaLiberacao destino) {
        assertThatThrownBy(() -> autorizacao.exigirTransicao(card(EtapaLiberacao.COMITE), destino, analista,
                List.of("Mychelly"), false))
                .isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    @DisplayName("Comitê→Origem com parecer faltando é permitido para analista (devolução)")
    void shouldAllowReturnToOrigemEvenWithPendingPareceres() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.ORIGEM, analista, List.of("Mychelly"), false))
                .isEmpty();
        assertThatCode(() -> autorizacao.exigirTransicao(card, EtapaLiberacao.ORIGEM, analista,
                List.of("Mychelly"), false)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Pendência→Aprovado não espera pareceres: a trava é só na saída do Comitê")
    void shouldNotApplyParecerLockOutsideComite() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);

        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.APROVADO, analista, List.of("Mychelly"), false))
                .isEmpty();
    }

    @Test
    @DisplayName("→Comitê sem ninguém marcado como Comitê: TransicaoInvalidaException")
    void shouldBlockMoveToComiteWhenComiteIsEmpty() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);

        assertThatThrownBy(() -> autorizacao.exigirTransicao(card, EtapaLiberacao.COMITE, comum, List.of(), true))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("Ninguém está marcado como Comitê");
    }

    @Test
    @DisplayName("comiteVazio só importa quando o destino é o Comitê")
    void shouldIgnoreEmptyComiteForOtherDestinations() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.ORIGEM, analista, List.of(), true)).isEmpty();
    }

    @Test
    @DisplayName("motivoBloqueio devolve o motivo de permissão antes do de regra")
    void shouldReturnPermissionMotiveBeforeRuleMotive() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        // não-analista num card que também tem parecer faltando: a recusa é de permissão
        Optional<String> motivo = autorizacao.motivoBloqueio(card, EtapaLiberacao.APROVADO, comum,
                List.of("Mychelly"), false);

        assertThat(motivo).contains("A partir do Comitê, só analista move o card.");
    }

    @Test
    @DisplayName("exigirTransicao: permissão vira AcessoNegado mesmo havendo regra violada")
    void shouldThrowAccessDeniedBeforeInvalidTransition() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThatThrownBy(() -> autorizacao.exigirTransicao(card, EtapaLiberacao.APROVADO, comum,
                List.of("Mychelly"), false))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    @DisplayName("motivo de permissão também precede 'comitê vazio'")
    void shouldReturnPermissionMotiveBeforeEmptyComiteRule() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);

        // Pendência→Comitê por não-analista: papel recusa antes de a regra do comitê vazio ser avaliada
        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.COMITE, comum, List.of(), true))
                .contains("A partir do Comitê, só analista move o card.");
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
        assertThat(LiberacaoAutorizacao.rotulo(EtapaLiberacao.APROVADO)).isEqualTo("Aprovado");
        assertThat(LiberacaoAutorizacao.rotulo(EtapaLiberacao.REPROVADO)).isEqualTo("Reprovado");
    }
}
