package com.portal.serasa.domain.model.liberacao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;

import static com.portal.serasa.domain.model.liberacao.EtapaLiberacao.APROVADO;
import static com.portal.serasa.domain.model.liberacao.EtapaLiberacao.COMITE;
import static com.portal.serasa.domain.model.liberacao.EtapaLiberacao.ORIGEM;
import static com.portal.serasa.domain.model.liberacao.EtapaLiberacao.PENDENCIA;
import static com.portal.serasa.domain.model.liberacao.EtapaLiberacao.REPROVADO;
import static org.assertj.core.api.Assertions.assertThat;

class EtapaLiberacaoTest {

    @Test
    @DisplayName("Origem só segue para o Comitê")
    void shouldOnlyLetOrigemGoToComite() {
        assertThat(ORIGEM.destinos()).containsExactlyInAnyOrder(COMITE);
    }

    @Test
    @DisplayName("Comitê decide (Pendência, Aprovado, Reprovado) ou devolve à Origem")
    void shouldLetComiteDecideOrReturnToOrigem() {
        assertThat(COMITE.destinos()).containsExactlyInAnyOrder(PENDENCIA, APROVADO, REPROVADO, ORIGEM);
    }

    @Test
    @DisplayName("Pendência decide ou volta ao Comitê, mas não à Origem")
    void shouldLetPendenciaDecideOrReturnToComite() {
        assertThat(PENDENCIA.destinos()).containsExactlyInAnyOrder(APROVADO, REPROVADO, COMITE);
        assertThat(PENDENCIA.aceita(ORIGEM)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"APROVADO", "REPROVADO"})
    @DisplayName("finalizado só volta ao Comitê (reabertura)")
    void shouldOnlyReopenFinalizedToComite(EtapaLiberacao terminal) {
        assertThat(terminal.destinos()).containsExactlyInAnyOrder(COMITE);
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
    @DisplayName("terminal(): só Aprovado e Reprovado")
    void shouldMarkOnlyApprovedAndRejectedAsTerminal() {
        assertThat(EnumSet.allOf(EtapaLiberacao.class).stream().filter(EtapaLiberacao::terminal))
                .containsExactlyInAnyOrder(APROVADO, REPROVADO);
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
    @EnumSource(value = EtapaLiberacao.class, names = {"PENDENCIA", "APROVADO", "REPROVADO"})
    @DisplayName("decisaoDoComite: Comitê→Pendência/Aprovado/Reprovado é decisão")
    void shouldTreatExitsFromComiteAsDecision(EtapaLiberacao para) {
        assertThat(EtapaLiberacao.decisaoDoComite(COMITE, para)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"ORIGEM", "PENDENCIA", "APROVADO", "REPROVADO"})
    @DisplayName("decisaoDoComite: só vale saindo do Comitê")
    void shouldNotTreatOtherOriginsAsComiteDecision(EtapaLiberacao de) {
        for (EtapaLiberacao para : EtapaLiberacao.values()) {
            assertThat(EtapaLiberacao.decisaoDoComite(de, para)).isFalse();
        }
    }
}
