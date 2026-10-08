package com.portal.serasa.application.service.notificacao;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Conexões abertas de Server-Sent Events, por usuário.
 *
 * <p>Fica em memória porque o backend roda numa instância só. Se um dia houver duas, o evento
 * gravado numa não chega aos navegadores ligados na outra — aí este hub precisa de um canal
 * comum (Postgres LISTEN/NOTIFY resolveria sem infraestrutura nova).</p>
 *
 * <p>Cada usuário pode ter várias abas, cada aba uma conexão. O batimento a cada 25 segundos
 * mantém a conexão viva em proxy que corta conexão parada, e é também como uma aba fechada é
 * descoberta: o envio falha e a conexão sai da lista.</p>
 */
@Slf4j
@Component
public class SseHub {

    /** Depois disso o navegador reconecta com ticket novo. Limita conexão esquecida aberta. */
    static final long DURACAO_CONEXAO_MS = Duration.ofMinutes(60).toMillis();
    private static final long BATIMENTO_S = 25;

    private final Map<UUID, List<Conexao>> conexoes = new ConcurrentHashMap<>();
    private final ScheduledExecutorService relogio = Executors.newSingleThreadScheduledExecutor(tarefa -> {
        Thread thread = new Thread(tarefa, "sse-batimento");
        thread.setDaemon(true);
        return thread;
    });

    /** SseEmitter não aceita envio concorrente; cada conexão serializa os seus. */
    private record Conexao(SseEmitter emitter) {
        synchronized void enviar(SseEmitter.SseEventBuilder evento) throws IOException {
            emitter.send(evento);
        }
    }

    public SseHub() {
        relogio.scheduleAtFixedRate(this::batimento, BATIMENTO_S, BATIMENTO_S, TimeUnit.SECONDS);
    }

    public SseEmitter conectar(UUID usuarioId) {
        return registrar(usuarioId, new SseEmitter(DURACAO_CONEXAO_MS));
    }

    /** Separado de {@link #conectar} para o teste trocar o emitter. */
    SseEmitter registrar(UUID usuarioId, SseEmitter emitter) {
        Conexao conexao = new Conexao(emitter);
        conexoes.computeIfAbsent(usuarioId, chave -> new CopyOnWriteArrayList<>()).add(conexao);
        Runnable remover = () -> remover(usuarioId, conexao);
        emitter.onCompletion(remover);
        emitter.onTimeout(remover);
        emitter.onError(erro -> remover.run());
        // Primeiro evento na hora: o navegador sabe que conectou sem esperar o batimento.
        tentar(usuarioId, conexao, SseEmitter.event().name("pronto").data("ok"));
        return emitter;
    }

    /** Para as abas de uma pessoa. */
    public void enviar(UUID usuarioId, String evento, Object dados) {
        List<Conexao> doUsuario = conexoes.get(usuarioId);
        if (doUsuario == null) {
            return;
        }
        for (Conexao conexao : doUsuario) {
            tentar(usuarioId, conexao, SseEmitter.event().name(evento).data(dados));
        }
    }

    /** Para todo mundo conectado: "o quadro mudou". */
    public void enviarTodos(String evento, Object dados) {
        conexoes.keySet().forEach(usuarioId -> enviar(usuarioId, evento, dados));
    }

    public int conectados() {
        return conexoes.values().stream().mapToInt(List::size).sum();
    }

    private void batimento() {
        conexoes.forEach((usuarioId, lista) -> lista.forEach(conexao ->
                tentar(usuarioId, conexao, SseEmitter.event().comment("batimento"))));
    }

    /**
     * Envia para uma conexão. Falha aqui nunca sai daqui: uma aba fechada no meio da lista não pode
     * impedir que as abas seguintes recebam o evento.
     */
    private void tentar(UUID usuarioId, Conexao conexao, SseEmitter.SseEventBuilder evento) {
        try {
            conexao.enviar(evento);
        } catch (Exception erro) {
            // Aba fechada ou rede caída. O navegador reconecta com ticket novo se ainda estiver lá.
            remover(usuarioId, conexao);
            try {
                conexao.emitter().completeWithError(erro);
            } catch (Exception jaEncerrada) {
                // A requisição assíncrona já tinha terminado; não há o que encerrar.
            }
            log.debug("Conexão SSE de {} removida: {}", usuarioId, erro.toString());
        }
    }

    private void remover(UUID usuarioId, Conexao conexao) {
        conexoes.computeIfPresent(usuarioId, (chave, lista) -> {
            lista.remove(conexao);
            return lista.isEmpty() ? null : lista;
        });
    }

    @PreDestroy
    void encerrar() {
        relogio.shutdownNow();
        conexoes.values().forEach(lista -> lista.forEach(conexao -> conexao.emitter().complete()));
        conexoes.clear();
    }
}
