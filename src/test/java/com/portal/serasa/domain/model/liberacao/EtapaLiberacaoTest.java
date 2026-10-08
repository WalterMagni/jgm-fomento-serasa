package com.portal.serasa.domain.model.liberacao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;

import static com.portal.serasa.domain.model.liberacao.EtapaLiberacao.COMITE;
import static com.portal.serasa.domain.model.liberacao.EtapaLiberacao.FINALIZADO;
import static com.portal.serasa.domain.model.liberacao.EtapaLiberacao.ORIGEM;
import static com.portal.serasa.domain.model.liberacao.EtapaLiberacao.PENDENCIA;
import static org.assertj.core.api.Assertions.assertThat;

class EtapaLiberacaoTest {

    @Test
    @DisplayName("a esteira tem quatro etapas: Origem, Comitê, Pendência e Finalizado")
    void shouldHaveFourStages() {
        assertThat(EtapaLiberacao.values()).containsExactly(ORIGEM, COMITE, PENDENCIA, FINALIZADO);
    }

    @Test
    @DisplayName("Origem só segue para o Comitê")
    void shouldOnlyLetOrigemGoToComite() {
        assertThat(ORIGEM.destinos()).containsExactlyInAnyOrder(COMITE);
    }

    @Test
    @DisplayName("Comitê decide (Pendência, Finalizado) ou devolve à Origem")
    void shouldLetComiteDecideOrReturnToOrigem() {
        assertThat(COMITE.destinos()).containsExactlyInAnyOrder(PENDENCIA, FINALIZADO, ORIGEM);
    }

    @Test
    @DisplayName("Pendência finaliza ou volta ao Comitê, mas não à Origem")
    void shouldLetPendenciaFinishOrReturnToComite() {
        assertThat(PENDENCIA.destinos()).containsExactlyInAnyOrder(FINALIZADO, COMITE);
        assertThat(PENDENCIA.aceita(ORIGEM)).isFalse();
    }

    @Test
    @DisplayName("Finalizado só volta ao Comitê (reabertura)")
    void shouldOnlyReopenFinalizedToComite() {
        assertThat(FINALIZADO.destinos()).containsExactlyInAnyOrder(COMITE);
        assertThat(FINALIZADO.aceita(ORIGEM)).isFalse();
        assertThat(FINALIZADO.aceita(PENDENCIA)).isFalse();
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
        assertThat(ORIGEM.terminal()).isFalse();
        assertThat(COMITE.terminal()).isFalse();
        assertThat(PENDENCIA.terminal()).isFalse();
    }

    @Test
    @DisplayName("decisaoDoComite: Comitê→Origem é devolução, não decisão")
    void shouldNotTreatReturnToOrigemAsDecision() {
        assertThat(EtapaLiberacao.decisaoDoComite(COMITE, ORIGEM)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"PENDENCIA", "FINALIZADO"})
    @DisplayName("decisaoDoComite: Comitê→Pendência/Finalizado é decisão")
    void shouldTreatExitsFromComiteAsDecision(EtapaLiberacao para) {
        assertThat(EtapaLiberacao.decisaoDoComite(COMITE, para)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"ORIGEM", "PENDENCIA", "FINALIZADO"})
    @DisplayName("decisaoDoComite: só vale saindo do Comitê")
    void shouldNotTreatOtherOriginsAsComiteDecision(EtapaLiberacao de) {
        for (EtapaLiberacao para : EtapaLiberacao.values()) {
            assertThat(EtapaLiberacao.decisaoDoComite(de, para)).isFalse();
        }
    }
}
