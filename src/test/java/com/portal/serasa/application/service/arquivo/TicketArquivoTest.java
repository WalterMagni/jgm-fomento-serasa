package com.portal.serasa.application.service.arquivo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TicketArquivoTest {

    private static final TicketArquivo.Arquivo PDF =
            new TicketArquivo.Arquivo("colinha.pdf", "application/pdf", new byte[]{1, 2, 3});

    @Test
    @DisplayName("o ticket abre o arquivo mais de uma vez enquanto vale — o visualizador de PDF pode pedir de novo")
    void shouldOpenRepeatedlyWhileValid() {
        TicketArquivo tickets = new TicketArquivo();
        AtomicInteger buscas = new AtomicInteger();
        String ticket = tickets.emitir(() -> {
            buscas.incrementAndGet();
            return PDF;
        });

        assertThat(buscas).hasValue(0);
        assertThat(tickets.abrir(ticket)).contains(PDF);
        assertThat(tickets.abrir(ticket)).contains(PDF);
        assertThat(buscas).hasValue(2);
    }

    @Test
    @DisplayName("depois de cinco minutos o ticket não abre mais nada")
    void shouldExpire() {
        Instant agora = Instant.parse("2026-10-24T12:00:00Z");
        MutableClock relogio = new MutableClock(agora);
        TicketArquivo tickets = new TicketArquivo(relogio);
        String ticket = tickets.emitir(() -> PDF);

        relogio.agora = agora.plus(Duration.ofMinutes(4));
        assertThat(tickets.abrir(ticket)).isPresent();

        relogio.agora = agora.plus(Duration.ofMinutes(5)).plusSeconds(1);
        assertThat(tickets.abrir(ticket)).isEmpty();
    }

    @Test
    @DisplayName("ticket desconhecido ou nulo não abre nada, e cada emissão gera um diferente")
    void shouldRejectUnknownAndBeUnpredictable() {
        TicketArquivo tickets = new TicketArquivo();
        assertThat(tickets.abrir("inventado")).isEmpty();
        assertThat(tickets.abrir(null)).isEmpty();
        assertThat(tickets.emitir(() -> PDF)).isNotEqualTo(tickets.emitir(() -> PDF)).hasSize(32);
    }

    private static final class MutableClock extends Clock {
        Instant agora;

        MutableClock(Instant agora) {
            this.agora = agora;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
