package com.portal.serasa.application.service.notificacao;

import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.domain.model.notificacao.TipoNotificacao;
import com.portal.serasa.infrastructure.persistence.entity.NotificacaoEntity;
import com.portal.serasa.infrastructure.persistence.repository.NotificacaoJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Grava notificações e entrega na hora a quem está com o portal aberto. */
@Service
@RequiredArgsConstructor
public class NotificacaoService {

    public static final String EVENTO_NOTIFICACAO = "notificacao";

    private final NotificacaoJpaRepository repository;
    private final SseHub hub;

    /** Uma notificação a criar, antes de virar linha. */
    public record Nova(UUID destinatarioId, TipoNotificacao tipo, String titulo, String resumo, String link) {
    }

    /** Notificação como a tela recebe, pelo canal e pela listagem. */
    public record Item(UUID id, TipoNotificacao tipo, String titulo, String resumo, String link,
                       String atorNome, LocalDateTime criadaEm, LocalDateTime lidaEm) {

        public static Item de(NotificacaoEntity entidade) {
            return new Item(entidade.getId(), entidade.getTipo(), entidade.getTitulo(), entidade.getResumo(),
                    entidade.getLink(), entidade.getAtorNome(), entidade.getCriadaEm(), entidade.getLidaEm());
        }
    }

    /** O que vai pelo canal: a notificação nova e a contagem de não lidas já atualizada. */
    public record Entrega(UUID destinatarioId, Item notificacao, long naoLidas) {
    }

    /**
     * Grava e entrega.
     *
     * <p>Roda em transação própria: é chamado depois do commit da ação que gerou a notificação, e
     * a falha aqui não pode desfazer a ação. Quem fez a ação nunca é notificado, e cada pessoa
     * recebe no máximo uma notificação por chamada — a primeira da lista vence, então quem chama
     * põe a mais específica primeiro (menção antes de "comentou").</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Entrega> notificar(Collection<Nova> novas, UUID atorId, String atorNome) {
        Map<UUID, Nova> porPessoa = new LinkedHashMap<>();
        for (Nova nova : novas) {
            if (nova.destinatarioId() != null && !nova.destinatarioId().equals(atorId)) {
                porPessoa.putIfAbsent(nova.destinatarioId(), nova);
            }
        }
        LocalDateTime agora = LocalDateTime.now();
        List<Entrega> entregas = new ArrayList<>();
        for (Nova nova : porPessoa.values()) {
            NotificacaoEntity salva = repository.save(NotificacaoEntity.builder()
                    .destinatarioId(nova.destinatarioId())
                    .tipo(nova.tipo())
                    .titulo(cortar(nova.titulo(), 300))
                    .resumo(cortar(nova.resumo(), 500))
                    .link(nova.link())
                    .atorId(atorId)
                    .atorNome(atorNome)
                    .criadaEm(agora)
                    .build());
            entregas.add(new Entrega(nova.destinatarioId(), Item.de(salva),
                    repository.countByDestinatarioIdAndLidaEmIsNull(nova.destinatarioId())));
        }
        return entregas;
    }

    /** Empurra pelo canal. Separado de {@link #notificar} para só sair depois do commit dela. */
    public void entregar(List<Entrega> entregas) {
        entregas.forEach(entrega -> hub.enviar(entrega.destinatarioId(), EVENTO_NOTIFICACAO,
                Map.of("notificacao", entrega.notificacao(), "naoLidas", entrega.naoLidas())));
    }

    @Transactional(readOnly = true)
    public List<Item> ultimas(UUID usuarioId, int limite) {
        return repository.findByDestinatarioIdOrderByCriadaEmDesc(usuarioId, PageRequest.of(0, Math.min(Math.max(limite, 1), 100)))
                .stream().map(Item::de).toList();
    }

    @Transactional(readOnly = true)
    public long naoLidas(UUID usuarioId) {
        return repository.countByDestinatarioIdAndLidaEmIsNull(usuarioId);
    }

    @Transactional
    public void marcarLida(UUID id, UUID usuarioId) {
        NotificacaoEntity notificacao = repository.findById(id)
                .filter(encontrada -> encontrada.getDestinatarioId().equals(usuarioId))
                .orElseThrow(() -> new EntityNotFoundException("Notificação não encontrada"));
        if (notificacao.getLidaEm() == null) {
            notificacao.setLidaEm(LocalDateTime.now());
            repository.save(notificacao);
        }
    }

    @Transactional
    public void marcarTodasLidas(UUID usuarioId) {
        repository.marcarTodasLidas(usuarioId, LocalDateTime.now());
    }

    @Transactional
    public void marcarLidasDoLink(UUID usuarioId, String link) {
        repository.marcarLidasDoLink(usuarioId, link, LocalDateTime.now());
    }

    private static String cortar(String texto, int limite) {
        if (texto == null || texto.length() <= limite) {
            return texto;
        }
        return texto.substring(0, limite - 1) + "…";
    }
}
