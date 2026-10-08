package com.portal.serasa.application.service.notificacao;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ticket de uso único para abrir o canal de notificações.
 *
 * <p>O EventSource do navegador não manda cabeçalho Authorization, então a credencial teria de ir
 * na URL — e URL fica em log de servidor, de proxy e no histórico. Em vez do JWT de 8 horas vai
 * este ticket: vale 60 segundos e uma conexão só. A aba pede um ticket com o JWT, normalmente, e
 * troca pelo canal em seguida.</p>
 */
@Component
public class TicketSse {

    static final long VALIDADE_S = 60;

    private final SecureRandom aleatorio = new SecureRandom();
    private final Map<String, Pendente> pendentes = new ConcurrentHashMap<>();

    private record Pendente(UUID usuarioId, Instant expira) {
    }

    public String emitir(UUID usuarioId) {
        limparVencidos();
        byte[] bytes = new byte[24];
        aleatorio.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pendentes.put(ticket, new Pendente(usuarioId, Instant.now().plusSeconds(VALIDADE_S)));
        return ticket;
    }

    /** Consome o ticket: a segunda tentativa com o mesmo valor falha. */
    public Optional<UUID> resgatar(String ticket) {
        if (ticket == null) {
            return Optional.empty();
        }
        Pendente pendente = pendentes.remove(ticket);
        if (pendente == null || pendente.expira().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(pendente.usuarioId());
    }

    private void limparVencidos() {
        Instant agora = Instant.now();
        pendentes.entrySet().removeIf(entrada -> entrada.getValue().expira().isBefore(agora));
    }
}
