package com.portal.serasa.application.service.notificacao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TicketSseTest {

    private final TicketSse tickets = new TicketSse();

    @Test
    @DisplayName("ticket vale uma vez só e devolve o dono")
    void shouldBeSingleUse() {
        UUID usuario = UUID.randomUUID();
        String ticket = tickets.emitir(usuario);
        assertThat(tickets.resgatar(ticket)).contains(usuario);
        assertThat(tickets.resgatar(ticket)).isEmpty();
    }

    @Test
    @DisplayName("ticket desconhecido ou nulo não abre canal")
    void shouldRejectUnknown() {
        assertThat(tickets.resgatar("inventado")).isEmpty();
        assertThat(tickets.resgatar(null)).isEmpty();
    }

    @Test
    @DisplayName("tickets diferentes a cada emissão, sem padding de base64")
    void shouldBeUnpredictable() {
        UUID usuario = UUID.randomUUID();
        String a = tickets.emitir(usuario);
        String b = tickets.emitir(usuario);
        assertThat(a).isNotEqualTo(b).doesNotContain("=").hasSize(32);
    }
}
