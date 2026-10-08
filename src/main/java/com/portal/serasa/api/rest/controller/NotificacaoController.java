package com.portal.serasa.api.rest.controller;

import com.portal.serasa.application.service.notificacao.NotificacaoService;
import com.portal.serasa.application.service.notificacao.SseHub;
import com.portal.serasa.application.service.notificacao.TicketSse;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sino de notificações: listagem, leitura e o canal em tempo real.
 *
 * <p>O canal ({@code /stream}) é a única rota autenticada por ticket em vez de JWT — ver
 * {@link TicketSse}. Ela está liberada no SecurityConfig e confere o ticket aqui.</p>
 */
@RestController
@RequestMapping("/api/v1/notificacoes")
@RequiredArgsConstructor
public class NotificacaoController {

    private final NotificacaoService notificacaoService;
    private final TicketSse ticketSse;
    private final SseHub hub;
    private final UsuarioLogado usuarioLogado;

    public record Listagem(long naoLidas, List<NotificacaoService.Item> itens) {
    }

    @GetMapping
    public ResponseEntity<Listagem> listar(@RequestParam(defaultValue = "20") int limite) {
        UserEntity usuario = usuarioLogado.obter();
        return ResponseEntity.ok(new Listagem(notificacaoService.naoLidas(usuario.getId()),
                notificacaoService.ultimas(usuario.getId(), limite)));
    }

    @PostMapping("/{id}/lida")
    public ResponseEntity<Void> marcarLida(@PathVariable UUID id) {
        notificacaoService.marcarLida(id, usuarioLogado.obter().getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Marca como lidas. Com {@code link}, só as que apontam para ele — abrir o card resolve as
     * notificações do card. Sem corpo, todas.
     */
    @PostMapping("/lidas")
    public ResponseEntity<Void> marcarLidas(@RequestBody(required = false) Map<String, String> corpo) {
        UUID usuarioId = usuarioLogado.obter().getId();
        String link = corpo == null ? null : corpo.get("link");
        if (link != null && !link.isBlank()) {
            notificacaoService.marcarLidasDoLink(usuarioId, link);
        } else {
            notificacaoService.marcarTodasLidas(usuarioId);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/ticket")
    public ResponseEntity<Map<String, String>> ticket() {
        return ResponseEntity.ok(Map.of("ticket", ticketSse.emitir(usuarioLogado.obter().getId())));
    }

    @GetMapping(path = "/stream", produces = "text/event-stream")
    public ResponseEntity<SseEmitter> stream(@RequestParam String ticket) {
        return ticketSse.resgatar(ticket)
                .map(usuarioId -> ResponseEntity.ok()
                        // Proxy que bufferiza resposta seguraria os eventos até encher o buffer.
                        .header("X-Accel-Buffering", "no")
                        .header("Cache-Control", "no-cache")
                        .body(hub.conectar(usuarioId)))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }
}
