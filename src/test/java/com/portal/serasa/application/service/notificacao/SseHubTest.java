package com.portal.serasa.application.service.notificacao;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SseHubTest {

    private final SseHub hub = new SseHub();

    @AfterEach
    void encerrar() {
        hub.encerrar();
    }

    /** Emitter que registra o que recebeu, ou falha sempre como uma aba já fechada. */
    private static final class EmitterFalso extends SseEmitter {
        final List<Object> recebidos = new ArrayList<>();
        final boolean morto;

        EmitterFalso(boolean morto) {
            this.morto = morto;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (morto) {
                throw new IOException("Broken pipe");
            }
            recebidos.add(builder);
        }

        @Override
        public synchronized void completeWithError(Throwable ex) {
            // Como o Spring faz com requisição assíncrona já encerrada.
            throw new IllegalStateException("já encerrado");
        }
    }

    @Test
    @DisplayName("aba fechada antes na lista não impede a aba viva de receber")
    void shouldDeliverToLiveConnectionAfterDeadOne() throws Exception {
        UUID usuario = UUID.randomUUID();
        EmitterFalso morto = new EmitterFalso(false);
        EmitterFalso vivo = new EmitterFalso(false);
        hub.registrar(usuario, morto);
        hub.registrar(usuario, vivo);
        // A primeira conexão morre depois de conectar, como uma aba fechada.
        var campoMorto = EmitterFalso.class.getDeclaredField("morto");
        campoMorto.setAccessible(true);
        campoMorto.setBoolean(morto, true);

        hub.enviar(usuario, "notificacao", Map.of("x", 1));

        assertThat(vivo.recebidos).hasSize(2); // "pronto" ao conectar + a notificação
        assertThat(hub.conectados()).isEqualTo(1);
    }

    @Test
    @DisplayName("enviarTodos alcança cada pessoa conectada")
    void shouldBroadcast() {
        EmitterFalso a = new EmitterFalso(false);
        EmitterFalso b = new EmitterFalso(false);
        hub.registrar(UUID.randomUUID(), a);
        hub.registrar(UUID.randomUUID(), b);
        hub.enviarTodos("quadro", Map.of("cardId", "1"));
        assertThat(a.recebidos).hasSize(2);
        assertThat(b.recebidos).hasSize(2);
    }
}
