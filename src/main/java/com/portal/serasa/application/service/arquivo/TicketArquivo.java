package com.portal.serasa.application.service.arquivo;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Link curto para abrir um arquivo protegido numa aba nova.
 *
 * <p>A aba nova não leva o cabeçalho Authorization, e o caminho anterior — baixar com o login e
 * abrir um blob — acabava em download no Safari e no Chrome configurado para baixar PDF. Com o
 * ticket, a aba abre um endereço de verdade, que o servidor responde para mostrar na tela.</p>
 *
 * <p>Diferente do {@code TicketSse}, vale por alguns minutos e mais de uma vez: o visualizador de
 * PDF do navegador pode pedir o arquivo em partes, e recarregar a aba não pode quebrar. O ticket
 * guarda como buscar o arquivo, não o arquivo: nada fica em memória além do caminho até ele.</p>
 */
@Component
public class TicketArquivo {

    static final long VALIDADE_S = 5 * 60;

    /** O que a rota pública devolve: nome, tipo e os bytes. */
    public record Arquivo(String nome, String contentType, byte[] dados) {
    }

    private record Pendente(Supplier<Arquivo> arquivo, Instant expira) {
    }

    private final SecureRandom aleatorio = new SecureRandom();
    private final Map<String, Pendente> pendentes = new ConcurrentHashMap<>();
    private final Clock relogio;

    public TicketArquivo() {
        this(Clock.systemUTC());
    }

    TicketArquivo(Clock relogio) {
        this.relogio = relogio;
    }

    /** Emitido por quem já está logado; quem tiver o link abre o arquivo até ele vencer. */
    public String emitir(Supplier<Arquivo> arquivo) {
        limparVencidos();
        byte[] bytes = new byte[24];
        aleatorio.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pendentes.put(ticket, new Pendente(arquivo, Instant.now(relogio).plusSeconds(VALIDADE_S)));
        return ticket;
    }

    /** Busca o arquivo do ticket, se ainda valer. O ticket continua válido até vencer. */
    public Optional<Arquivo> abrir(String ticket) {
        if (ticket == null) {
            return Optional.empty();
        }
        Pendente pendente = pendentes.get(ticket);
        if (pendente == null || pendente.expira().isBefore(Instant.now(relogio))) {
            pendentes.remove(ticket);
            return Optional.empty();
        }
        return Optional.of(pendente.arquivo().get());
    }

    private void limparVencidos() {
        Instant agora = Instant.now(relogio);
        pendentes.entrySet().removeIf(entrada -> entrada.getValue().expira().isBefore(agora));
    }
}
