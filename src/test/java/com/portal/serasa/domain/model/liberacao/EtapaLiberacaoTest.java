package com.portal.serasa.domain.model.liberacao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Set;

import static com.portal.serasa.domain.model.liberacao.EtapaLiberacao.APROVADO;
import static com.portal.serasa.domain.model.liberacao.EtapaLiberacao.ASSINATURA;
import static com.portal.serasa.domain.model.liberacao.EtapaLiberacao.COMITE;
import static com.portal.serasa.domain.model.liberacao.EtapaLiberacao.DIRETORIA;
import static com.portal.serasa.domain.model.liberacao.EtapaLiberacao.FINALIZADO;
import static com.portal.serasa.domain.model.liberacao.EtapaLiberacao.ORIGEM;
import static com.portal.serasa.domain.model.liberacao.EtapaLiberacao.PENDENCIA;
import static com.portal.serasa.domain.model.liberacao.EtapaLiberacao.REPROVADO;
import static org.assertj.core.api.Assertions.assertThat;

class EtapaLiberacaoTest {

    @Test
    @DisplayName("a esteira tem oito etapas, na ordem: Origem, Comitê, Pendência, Diretoria, Aprovado, Reprovado, Assinatura e Finalizado")
    void shouldHaveEightStagesInOrder() {
        assertThat(EtapaLiberacao.values())
                .containsExactly(ORIGEM, COMITE, PENDENCIA, DIRETORIA, APROVADO, REPROVADO, ASSINATURA, FINALIZADO);
    }

    @Test
    @DisplayName("Origem segue para o Comitê ou para a Pendência (o que falta se resolve antes do Comitê)")
    void shouldLetOrigemGoToComiteOrPendencia() {
        assertThat(ORIGEM.destinos()).containsExactlyInAnyOrder(COMITE, PENDENCIA);
        assertThat(ORIGEM.aceita(APROVADO)).isFalse();
        assertThat(ORIGEM.aceita(DIRETORIA)).isFalse();
    }

    @Test
    @DisplayName("Comitê pede Pendência, manda para a Diretoria, decide Aprovado ou Reprovado, ou devolve à Origem; não vai direto a Finalizado")
    void shouldLetComiteDecideOrReturnToOrigem() {
        assertThat(COMITE.destinos()).containsExactlyInAnyOrder(PENDENCIA, DIRETORIA, APROVADO, REPROVADO, ORIGEM);
        assertThat(COMITE.aceita(FINALIZADO)).isFalse();
        assertThat(COMITE.aceita(ASSINATURA)).isFalse();
    }

    @Test
    @DisplayName("Pendência manda para a Diretoria, decide (Aprovado ou Reprovado) ou volta ao Comitê, mas não à Origem")
    void shouldLetPendenciaDecideOrReturnToComite() {
        assertThat(PENDENCIA.destinos()).containsExactlyInAnyOrder(DIRETORIA, APROVADO, REPROVADO, COMITE);
        assertThat(PENDENCIA.aceita(ORIGEM)).isFalse();
        assertThat(PENDENCIA.aceita(FINALIZADO)).isFalse();
    }

    @Test
    @DisplayName("Diretoria só aprova ou reprova: não devolve ao Comitê, à Pendência nem à Origem")
    void shouldLetDiretoriaOnlyApproveOrReject() {
        assertThat(DIRETORIA.destinos()).containsExactlyInAnyOrder(APROVADO, REPROVADO);
        assertThat(DIRETORIA.aceita(COMITE)).isFalse();
        assertThat(DIRETORIA.aceita(PENDENCIA)).isFalse();
        assertThat(DIRETORIA.aceita(ORIGEM)).isFalse();
        assertThat(DIRETORIA.aceita(ASSINATURA)).isFalse();
        assertThat(DIRETORIA.aceita(FINALIZADO)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"ORIGEM", "APROVADO", "REPROVADO", "ASSINATURA", "FINALIZADO"})
    @DisplayName("só Comitê e Pendência chegam à Diretoria")
    void shouldOnlyReachDiretoriaFromComiteOrPendencia(EtapaLiberacao de) {
        assertThat(de.aceita(DIRETORIA)).isFalse();
    }

    @Test
    @DisplayName("Diretoria não é etapa decidida nem decisão: o card ainda espera a palavra final")
    void shouldNotTreatDiretoriaAsDecidedNorDecision() {
        assertThat(DIRETORIA.decidida()).isFalse();
        assertThat(DIRETORIA.decisao()).isFalse();
        assertThat(DIRETORIA.terminal()).isFalse();
    }

    @Test
    @DisplayName("Aprovado segue para Assinatura ou é reaberto no Comitê")
    void shouldLetAprovadoGoToAssinaturaOrReopen() {
        assertThat(APROVADO.destinos()).containsExactlyInAnyOrder(ASSINATURA, COMITE);
        assertThat(APROVADO.aceita(FINALIZADO)).isFalse();
        assertThat(APROVADO.aceita(REPROVADO)).isFalse();
    }

    @Test
    @DisplayName("Reprovado é finalizado ou reaberto no Comitê; não passa por Assinatura")
    void shouldLetReprovadoFinishOrReopen() {
        assertThat(REPROVADO.destinos()).containsExactlyInAnyOrder(FINALIZADO, COMITE);
        assertThat(REPROVADO.aceita(ASSINATURA)).isFalse();
    }

    @Test
    @DisplayName("Assinatura é finalizada ou reaberta no Comitê")
    void shouldLetAssinaturaFinishOrReopen() {
        assertThat(ASSINATURA.destinos()).containsExactlyInAnyOrder(FINALIZADO, COMITE);
        assertThat(ASSINATURA.aceita(APROVADO)).isFalse();
    }

    @Test
    @DisplayName("Finalizado só volta ao Comitê (reabertura)")
    void shouldOnlyReopenFinalizedToComite() {
        assertThat(FINALIZADO.destinos()).containsExactlyInAnyOrder(COMITE);
        assertThat(FINALIZADO.aceita(ORIGEM)).isFalse();
        assertThat(FINALIZADO.aceita(PENDENCIA)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"APROVADO", "REPROVADO", "ASSINATURA", "FINALIZADO"})
    @DisplayName("de toda etapa decidida dá para voltar ao Comitê")
    void shouldAlwaysAllowReopeningDecidedStages(EtapaLiberacao etapa) {
        assertThat(etapa.aceita(COMITE)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(EtapaLiberacao.class)
    @DisplayName("nenhuma etapa vai para si mesma")
    void shouldNeverAcceptItself(EtapaLiberacao etapa) {
        assertThat(etapa.aceita(etapa)).isFalse();
    }

    @Test
    @DisplayName("aceita() concorda com destinos() em todos os pares")
    void shouldKeepAceitaConsistentWithDestinos() {
        for (EtapaLiberacao de : EtapaLiberacao.values()) {
            for (EtapaLiberacao para : EtapaLiberacao.values()) {
                assertThat(de.aceita(para)).isEqualTo(de.destinos().contains(para));
            }
        }
    }

    @Test
    @DisplayName("terminal(): só Finalizado")
    void shouldMarkOnlyFinalizadoAsTerminal() {
        assertThat(EnumSet.allOf(EtapaLiberacao.class).stream().filter(EtapaLiberacao::terminal))
                .containsExactlyInAnyOrder(FINALIZADO);
    }

    @Test
    @DisplayName("decisao(): só Aprovado e Reprovado")
    void shouldMarkOnlyAprovadoAndReprovadoAsDecisao() {
        assertThat(EnumSet.allOf(EtapaLiberacao.class).stream().filter(EtapaLiberacao::decisao))
                .containsExactlyInAnyOrder(APROVADO, REPROVADO);
    }

    @Test
    @DisplayName("decidida(): Aprovado, Reprovado, Assinatura e Finalizado")
    void shouldMarkDecidedStages() {
        assertThat(EnumSet.allOf(EtapaLiberacao.class).stream().filter(EtapaLiberacao::decidida))
                .containsExactlyInAnyOrder(APROVADO, REPROVADO, ASSINATURA, FINALIZADO);
        assertThat(Set.of(ORIGEM, COMITE, PENDENCIA, DIRETORIA)).noneMatch(EtapaLiberacao::decidida);
    }

    @ParameterizedTest
    @EnumSource(EtapaLiberacao.class)
    @DisplayName("toda decisão e todo terminal é etapa decidida")
    void shouldKeepDecisionAndTerminalInsideDecided(EtapaLiberacao etapa) {
        if (etapa.decisao() || etapa.terminal()) {
            assertThat(etapa.decidida()).isTrue();
        }
    }

    @Test
    @DisplayName("reabertura: só de etapa decidida para o Comitê")
    void shouldOnlyReopenFromDecidedToComite() {
        for (EtapaLiberacao de : EtapaLiberacao.values()) {
            for (EtapaLiberacao para : EtapaLiberacao.values()) {
                assertThat(EtapaLiberacao.reabertura(de, para))
                        .as("%s -> %s", de, para)
                        .isEqualTo(de.decidida() && para == COMITE);
            }
        }
        assertThat(EtapaLiberacao.reabertura(PENDENCIA, COMITE)).isFalse();
        assertThat(EtapaLiberacao.reabertura(DIRETORIA, COMITE)).isFalse();
        assertThat(EtapaLiberacao.reabertura(ASSINATURA, COMITE)).isTrue();
        assertThat(EtapaLiberacao.reabertura(APROVADO, ASSINATURA)).isFalse();
    }

    @Test
    @DisplayName("decisaoDoComite: Comitê→Origem é devolução, não decisão")
    void shouldNotTreatReturnToOrigemAsDecision() {
        assertThat(EtapaLiberacao.decisaoDoComite(COMITE, ORIGEM)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"PENDENCIA", "DIRETORIA", "APROVADO", "REPROVADO"})
    @DisplayName("decisaoDoComite: Comitê→Pendência/Diretoria/Aprovado/Reprovado é decisão")
    void shouldTreatExitsFromComiteAsDecision(EtapaLiberacao para) {
        assertThat(EtapaLiberacao.decisaoDoComite(COMITE, para)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"ORIGEM", "PENDENCIA", "DIRETORIA", "APROVADO", "REPROVADO", "ASSINATURA", "FINALIZADO"})
    @DisplayName("decisaoDoComite: só vale saindo do Comitê (a decisão da Diretoria não pede parecer do Comitê)")
    void shouldNotTreatOtherOriginsAsComiteDecision(EtapaLiberacao de) {
        for (EtapaLiberacao para : EtapaLiberacao.values()) {
            assertThat(EtapaLiberacao.decisaoDoComite(de, para)).isFalse();
        }
    }
}
