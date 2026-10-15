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
    private final UserEntity diretor = UserEntity.builder().id(UUID.randomUUID()).name("Diretor").analista(true).diretoria(true).build();

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
    @EnumSource(value = EtapaLiberacao.class, names = {"COMITE", "PENDENCIA", "DIRETORIA", "APROVADO", "REPROVADO", "ASSINATURA", "FINALIZADO"})
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
        assertThatThrownBy(() -> autorizacao.exigirTransicao(card, EtapaLiberacao.COMITE, null, 0, false, false))
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

        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.COMITE, comum, 0, false, false)).isEmpty();
        assertThatCode(() -> autorizacao.exigirTransicao(card, EtapaLiberacao.COMITE, comum, 0, false, false))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"COMITE", "PENDENCIA", "APROVADO", "REPROVADO", "ASSINATURA", "FINALIZADO"})
    @DisplayName("não-analista não move card do Comitê em diante, para nenhum destino válido")
    void shouldDenyMoveByNonAnalystFromComiteOn(EtapaLiberacao origem) {
        LiberacaoCardEntity card = card(origem);

        for (EtapaLiberacao destino : origem.destinos()) {
            assertThat(autorizacao.motivoBloqueio(card, destino, comum, 2, false, false))
                    .contains("A partir do Comitê, só analista move o card.");
            assertThatThrownBy(() -> autorizacao.exigirTransicao(card, destino, comum, 2, false, false))
                    .isInstanceOf(AcessoNegadoException.class)
                    .hasMessage("A partir do Comitê, só analista move o card.");
        }
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"COMITE", "PENDENCIA", "APROVADO", "REPROVADO", "ASSINATURA", "FINALIZADO"})
    @DisplayName("analista move card do Comitê em diante por qualquer caminho válido (com parecer registrado)")
    void shouldLetAnalystMoveFromComiteOn(EtapaLiberacao origem) {
        LiberacaoCardEntity card = card(origem);

        for (EtapaLiberacao destino : origem.destinos()) {
            assertThat(autorizacao.motivoBloqueio(card, destino, analista, 1, false, false)).isEmpty();
            assertThatCode(() -> autorizacao.exigirTransicao(card, destino, analista, 1, false, false))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("caminho inválido: motivo 'Não dá para ir de X para Y.'")
    void shouldExplainInvalidPath() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);

        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.FINALIZADO, analista, 0, false, false))
                .hasValueSatisfying(motivo -> assertThat(motivo)
                        .startsWith("Não dá para ir")
                        .isEqualTo("Não dá para ir de Origem para Finalizados."));
        assertThat(autorizacao.motivoBloqueio(card(EtapaLiberacao.FINALIZADO), EtapaLiberacao.PENDENCIA, analista, 0, false, false))
                .contains("Não dá para ir de Finalizados para Pendência.");
    }

    @Test
    @DisplayName("caminho inválido vale também para analista, e o motivo vem antes da regra de papel")
    void shouldCheckPathBeforeRole() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);

        // não-analista em Pendência→Origem: o caminho inválido é o motivo, não o papel
        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.ORIGEM, comum, 0, false, false))
                .contains("Não dá para ir de Pendência para Origem.");
        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.ORIGEM, analista, 0, false, false))
                .contains("Não dá para ir de Pendência para Origem.");
    }

    @Test
    @DisplayName("exigirTransicao: caminho inválido é TransicaoInvalidaException (409), não AcessoNegado")
    void shouldThrowInvalidTransitionForInvalidPath() {
        LiberacaoCardEntity card = card(EtapaLiberacao.FINALIZADO);

        assertThatThrownBy(() -> autorizacao.exigirTransicao(card, EtapaLiberacao.ORIGEM, comum, 2, false, false))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Não dá para ir de Finalizados para Origem.");
    }

    // ------------------------------------------- Comitê: pelo menos um parecer

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"PENDENCIA", "DIRETORIA", "APROVADO", "REPROVADO"})
    @DisplayName("Comitê→Pendência/Diretoria/Aprovado/Reprovado sem nenhum parecer registrado: 'Precisa de pelo menos um parecer do Comitê.'")
    void shouldBlockLeavingComiteWithoutAnyParecer(EtapaLiberacao destino) {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThat(autorizacao.motivoBloqueio(card, destino, analista, 0, false, false))
                .contains("Precisa de pelo menos um parecer do Comitê.");
        assertThatThrownBy(() -> autorizacao.exigirTransicao(card, destino, analista, 0, false, false))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Precisa de pelo menos um parecer do Comitê.");
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"PENDENCIA", "DIRETORIA", "APROVADO", "REPROVADO"})
    @DisplayName("Comitê→Pendência/Diretoria/Aprovado/Reprovado com um parecer (de dois esperados) já é liberado")
    void shouldAllowLeavingComiteWithOneParecer(EtapaLiberacao destino) {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        // a contagem vem de quem registrou; quem falta aparece só como aviso()
        assertThat(autorizacao.motivoBloqueio(card, destino, analista, 1, false, false)).isEmpty();
        assertThatCode(() -> autorizacao.exigirTransicao(card, destino, analista, 1, false, false))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("com todos os pareceres registrados também libera")
    void shouldAllowLeavingComiteWithAllPareceres() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.APROVADO, analista, 5, false, false)).isEmpty();
    }

    @Test
    @DisplayName("Comitê→Origem sem nenhum parecer é permitido para analista (devolução)")
    void shouldAllowReturnToOrigemWithoutParecer() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.ORIGEM, analista, 0, false, false)).isEmpty();
        assertThatCode(() -> autorizacao.exigirTransicao(card, EtapaLiberacao.ORIGEM, analista, 0, false, false))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Pendência→Aprovado não pede parecer: a trava é só na saída do Comitê")
    void shouldNotApplyParecerRuleOutsideComite() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);

        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.APROVADO, analista, 0, false, false)).isEmpty();
        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.COMITE, analista, 0, false, false)).isEmpty();
    }

    @Test
    @DisplayName("reabrir Finalizado→Comitê não pede parecer (a rodada nova começa vazia)")
    void shouldNotRequireParecerToReopen() {
        LiberacaoCardEntity card = card(EtapaLiberacao.FINALIZADO);

        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.COMITE, analista, 0, false, false)).isEmpty();
    }

    // --------------------------------------------------------------- aviso

    @Test
    @DisplayName("aviso: uma pessoa faltando usa o singular")
    void shouldWarnInSingularWhenOneParecerIsMissing() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThat(autorizacao.aviso(card, EtapaLiberacao.APROVADO, List.of("Mychelly")))
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

        assertThat(autorizacao.aviso(card, EtapaLiberacao.APROVADO, List.of("Ana", "Bia", "Carla")))
                .contains("Ana, Bia e Carla ainda não deram parecer.");
    }

    @Test
    @DisplayName("aviso: ninguém faltando = sem aviso")
    void shouldNotWarnWhenNobodyIsMissing() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThat(autorizacao.aviso(card, EtapaLiberacao.APROVADO, List.of())).isEmpty();
    }

    @Test
    @DisplayName("aviso: devolver à Origem não avisa, mesmo com parecer faltando")
    void shouldNotWarnOnReturnToOrigem() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThat(autorizacao.aviso(card, EtapaLiberacao.ORIGEM, List.of("Mychelly"))).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"ORIGEM", "PENDENCIA", "DIRETORIA", "APROVADO", "REPROVADO", "ASSINATURA", "FINALIZADO"})
    @DisplayName("aviso: só vale saindo do Comitê (a Diretoria decide sem parecer do Comitê)")
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

        assertThatThrownBy(() -> autorizacao.exigirTransicao(card, EtapaLiberacao.COMITE, comum, 0, true, false))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("Ninguém está marcado como Comitê");
    }

    @Test
    @DisplayName("comiteVazio só importa quando o destino é o Comitê")
    void shouldIgnoreEmptyComiteForOtherDestinations() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.ORIGEM, analista, 0, true, false)).isEmpty();
    }

    @Test
    @DisplayName("motivoBloqueio devolve o motivo de permissão antes do de regra")
    void shouldReturnPermissionMotiveBeforeRuleMotive() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        // não-analista num card sem nenhum parecer: a recusa é de permissão
        Optional<String> motivo = autorizacao.motivoBloqueio(card, EtapaLiberacao.APROVADO, comum, 0, false, false);

        assertThat(motivo).contains("A partir do Comitê, só analista move o card.");
    }

    @Test
    @DisplayName("exigirTransicao: permissão vira AcessoNegado mesmo havendo regra violada")
    void shouldThrowAccessDeniedBeforeInvalidTransition() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThatThrownBy(() -> autorizacao.exigirTransicao(card, EtapaLiberacao.APROVADO, comum, 0, false, false))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    @DisplayName("motivo de permissão também precede 'comitê vazio'")
    void shouldReturnPermissionMotiveBeforeEmptyComiteRule() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);

        // Pendência→Comitê por não-analista: papel recusa antes de a regra do comitê vazio ser avaliada
        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.COMITE, comum, 0, true, false))
                .contains("A partir do Comitê, só analista move o card.");
    }

    @Test
    @DisplayName("reabrir Finalizado→Comitê com o Comitê vazio é recusado")
    void shouldBlockReopenWhenComiteIsEmpty() {
        LiberacaoCardEntity card = card(EtapaLiberacao.FINALIZADO);

        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.COMITE, analista, 0, true, false))
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

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"APROVADO", "REPROVADO", "ASSINATURA", "FINALIZADO"})
    @DisplayName("reabrir etapa decidida→Comitê: analista passa sem parecer; não-analista é negado; Comitê vazio é recusado")
    void shouldAuthorizeReopeningFromEveryDecidedStage(EtapaLiberacao etapa) {
        LiberacaoCardEntity card = card(etapa);

        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.COMITE, analista, 0, false, false)).isEmpty();
        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.COMITE, comum, 0, false, false))
                .contains("A partir do Comitê, só analista move o card.");
        assertThatThrownBy(() -> autorizacao.exigirTransicao(card, EtapaLiberacao.COMITE, analista, 0, true, false))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("Ninguém está marcado como Comitê");
    }

    @Test
    @DisplayName("caminhos que a nova esteira não permite: Aprovado→Finalizado, Reprovado→Assinatura, Comitê→Assinatura")
    void shouldExplainPathsTheNewPipelineDoesNotAllow() {
        assertThat(autorizacao.motivoBloqueio(card(EtapaLiberacao.APROVADO), EtapaLiberacao.FINALIZADO, analista, 1, false, false))
                .contains("Não dá para ir de Aprovado para Finalizados.");
        assertThat(autorizacao.motivoBloqueio(card(EtapaLiberacao.REPROVADO), EtapaLiberacao.ASSINATURA, analista, 1, false, false))
                .contains("Não dá para ir de Reprovado para Assinatura.");
        assertThat(autorizacao.motivoBloqueio(card(EtapaLiberacao.COMITE), EtapaLiberacao.ASSINATURA, analista, 1, false, false))
                .contains("Não dá para ir de Comitê para Assinatura.");
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"DIRETORIA", "APROVADO", "REPROVADO"})
    @DisplayName("aviso de parecer faltando também vale para Comitê→Diretoria/Aprovado/Reprovado")
    void shouldWarnWhenDecidingWithMissingParecer(EtapaLiberacao destino) {
        assertThat(autorizacao.aviso(card(EtapaLiberacao.COMITE), destino, List.of("Mychelly")))
                .contains("Mychelly ainda não deu parecer.");
    }

    @Test
    @DisplayName("rótulos das etapas em português")
    void shouldLabelEtapasInPortuguese() {
        assertThat(LiberacaoAutorizacao.rotulo(EtapaLiberacao.ORIGEM)).isEqualTo("Origem");
        assertThat(LiberacaoAutorizacao.rotulo(EtapaLiberacao.COMITE)).isEqualTo("Comitê");
        assertThat(LiberacaoAutorizacao.rotulo(EtapaLiberacao.PENDENCIA)).isEqualTo("Pendência");
        assertThat(LiberacaoAutorizacao.rotulo(EtapaLiberacao.DIRETORIA)).isEqualTo("Diretoria");
        assertThat(LiberacaoAutorizacao.rotulo(EtapaLiberacao.APROVADO)).isEqualTo("Aprovado");
        assertThat(LiberacaoAutorizacao.rotulo(EtapaLiberacao.REPROVADO)).isEqualTo("Reprovado");
        assertThat(LiberacaoAutorizacao.rotulo(EtapaLiberacao.ASSINATURA)).isEqualTo("Assinatura");
        assertThat(LiberacaoAutorizacao.rotulo(EtapaLiberacao.FINALIZADO)).isEqualTo("Finalizados");
    }

    // ---------------------------------------------------------------- Diretoria

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"COMITE", "PENDENCIA"})
    @DisplayName("→Diretoria sem ninguém marcado como Diretoria: TransicaoInvalidaException com a orientação ao admin")
    void shouldBlockMoveToDiretoriaWhenDiretoriaIsEmpty(EtapaLiberacao origem) {
        LiberacaoCardEntity card = card(origem);

        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.DIRETORIA, analista, 1, false, true))
                .contains("Ninguém está marcado como Diretoria. Peça ao admin para marcar em Configurações.");
        assertThatThrownBy(() -> autorizacao.exigirTransicao(card, EtapaLiberacao.DIRETORIA, analista, 1, false, true))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Ninguém está marcado como Diretoria. Peça ao admin para marcar em Configurações.");
        // com a Diretoria preenchida o mesmo movimento passa
        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.DIRETORIA, analista, 1, false, false)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"PENDENCIA", "APROVADO", "REPROVADO", "ORIGEM"})
    @DisplayName("Diretoria vazia só barra quem vai para a Diretoria; os outros destinos do Comitê seguem livres")
    void shouldIgnoreEmptyDiretoriaForOtherDestinations(EtapaLiberacao destino) {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThat(autorizacao.motivoBloqueio(card, destino, analista, 1, false, true)).isEmpty();
    }

    @Test
    @DisplayName("Comitê vazio não barra o caminho para a Diretoria: cada marca vale só para o seu destino")
    void shouldIgnoreEmptyComiteWhenGoingToDiretoria() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.DIRETORIA, analista, 1, true, false)).isEmpty();
    }

    @Test
    @DisplayName("Diretoria vazia: a recusa de permissão vem antes (não-analista numa Pendência recebe o motivo de papel)")
    void shouldReportRoleBeforeEmptyDiretoria() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);

        assertThat(autorizacao.motivoBloqueio(card, EtapaLiberacao.DIRETORIA, comum, 1, false, true))
                .contains("A partir do Comitê, só analista move o card.");
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"APROVADO", "REPROVADO"})
    @DisplayName("com o card na Diretoria só quem tem o perfil Diretoria move; analista comum e não-analista recebem o motivo da Diretoria")
    void shouldLetOnlyDiretoriaMoveCardFromDiretoria(EtapaLiberacao destino) {
        LiberacaoCardEntity card = card(EtapaLiberacao.DIRETORIA);

        // a Diretoria decide sem parecer do Comitê: o card já chega com a rodada resolvida
        assertThat(autorizacao.motivoBloqueio(card, destino, diretor, 0, false, false)).isEmpty();
        assertThatCode(() -> autorizacao.exigirTransicao(card, destino, diretor, 0, false, false))
                .doesNotThrowAnyException();

        for (UserEntity quem : List.of(analista, comum)) {
            assertThat(autorizacao.motivoBloqueio(card, destino, quem, 5, false, false))
                    .contains("Na Diretoria, só quem tem o perfil Diretoria decide.");
            assertThatThrownBy(() -> autorizacao.exigirTransicao(card, destino, quem, 5, false, false))
                    .isInstanceOf(AcessoNegadoException.class)
                    .hasMessage("Na Diretoria, só quem tem o perfil Diretoria decide.");
        }
        assertThat(autorizacao.motivoBloqueio(card, destino, null, 0, false, false))
                .contains("Na Diretoria, só quem tem o perfil Diretoria decide.");
        assertThatThrownBy(() -> autorizacao.exigirTransicao(card, destino, null, 0, false, false))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessage("Não autenticado");
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"ORIGEM", "COMITE", "PENDENCIA", "ASSINATURA", "FINALIZADO"})
    @DisplayName("a Diretoria não devolve: qualquer destino fora de Aprovado/Reprovado é caminho inválido (409), para quem for")
    void shouldExplainThatDiretoriaDoesNotGoBack(EtapaLiberacao destino) {
        LiberacaoCardEntity card = card(EtapaLiberacao.DIRETORIA);
        String esperado = "Não dá para ir de Diretoria para " + LiberacaoAutorizacao.rotulo(destino) + ".";

        for (UserEntity quem : List.of(diretor, analista, comum)) {
            assertThat(autorizacao.motivoBloqueio(card, destino, quem, 5, false, false)).contains(esperado);
            assertThatThrownBy(() -> autorizacao.exigirTransicao(card, destino, quem, 5, false, false))
                    .isInstanceOf(TransicaoInvalidaException.class)
                    .hasMessage(esperado);
        }
    }

    @Test
    @DisplayName("quem tem o perfil Diretoria continua analista: move os cards das outras etapas como qualquer analista")
    void shouldKeepDiretoriaUserAsAnalystInOtherStages() {
        assertThat(autorizacao.motivoBloqueio(card(EtapaLiberacao.COMITE), EtapaLiberacao.APROVADO, diretor, 1, false, false)).isEmpty();
        assertThat(autorizacao.motivoBloqueio(card(EtapaLiberacao.FINALIZADO), EtapaLiberacao.COMITE, diretor, 0, false, false)).isEmpty();
        assertThat(autorizacao.podeEditar(card(EtapaLiberacao.DIRETORIA), diretor)).isTrue();
    }

    @Test
    @DisplayName("exigirDiretoria: só quem tem o perfil passa; analista comum e não-analista recebem 'Só a Diretoria pode <ação>.'")
    void shouldRequireDiretoriaForAction() {
        assertThatCode(() -> autorizacao.exigirDiretoria(diretor, "marcar o quadrado D")).doesNotThrowAnyException();
        assertThatThrownBy(() -> autorizacao.exigirDiretoria(analista, "marcar o quadrado D"))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessage("Só a Diretoria pode marcar o quadrado D.");
        assertThatThrownBy(() -> autorizacao.exigirDiretoria(comum, "decidir"))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessage("Só a Diretoria pode decidir.");
        assertThatThrownBy(() -> autorizacao.exigirDiretoria(null, "decidir"))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessage("Não autenticado");
    }

    @Test
    @DisplayName("aviso: Diretoria→Aprovado/Reprovado não avisa de parecer faltando, e Comitê→Diretoria avisa")
    void shouldWarnOnlyWhenComiteSendsToDiretoria() {
        assertThat(autorizacao.aviso(card(EtapaLiberacao.COMITE), EtapaLiberacao.DIRETORIA, List.of("Mychelly", "Carla")))
                .contains("Mychelly e Carla ainda não deram parecer.");
        assertThat(autorizacao.aviso(card(EtapaLiberacao.DIRETORIA), EtapaLiberacao.APROVADO, List.of("Mychelly"))).isEmpty();
        assertThat(autorizacao.aviso(card(EtapaLiberacao.PENDENCIA), EtapaLiberacao.DIRETORIA, List.of("Mychelly"))).isEmpty();
    }
}
