package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.application.service.notificacao.NotificacaoService;
import com.portal.serasa.application.service.notificacao.NotificacaoService.Nova;
import com.portal.serasa.application.service.notificacao.SseHub;
import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import com.portal.serasa.domain.model.notificacao.TipoNotificacao;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoCardEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoMembroEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoParecerEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoMembroJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoParecerJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Decide quem fica sabendo de cada ação na esteira, depois que ela foi gravada.
 *
 * <p>A tabela do design (2026-10-07, seção 4) está toda aqui. Menção vem sempre primeiro: quem foi
 * marcado num comentário recebe "te marcou", não um segundo aviso de "comentou".</p>
 *
 * <p>Toda ação também avisa os navegadores abertos de que o quadro mudou, para quem está olhando
 * ver o card se mexer sem recarregar.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiberacaoNotificador {

    public static final String EVENTO_QUADRO = "quadro";

    private final NotificacaoService notificacaoService;
    private final SseHub hub;
    private final LiberacaoMembroJpaRepository membroRepository;
    private final LiberacaoParecerJpaRepository parecerRepository;
    private final UserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoAcontecer(LiberacaoEvento evento) {
        try {
            List<Nova> novas = destinatarios(evento);
            UserEntity autor = evento.autor();
            if (!novas.isEmpty()) {
                notificacaoService.entregar(notificacaoService.notificar(novas, autor.getId(), autor.getName()));
            }
        } catch (RuntimeException erro) {
            // A ação já foi gravada; falhar o aviso não pode virar erro para quem agiu.
            log.error("Falha ao notificar evento {} do card {}", evento.getClass().getSimpleName(), evento.card().getId(), erro);
        }
        hub.enviarTodos(EVENTO_QUADRO, Map.of("cardId", evento.card().getId()));
    }

    List<Nova> destinatarios(LiberacaoEvento evento) {
        LiberacaoCardEntity card = evento.card();
        String autor = primeiroNome(evento.autor().getName());
        String ref = "#" + card.getNumero();
        String link = link(card);
        List<Nova> novas = new ArrayList<>();

        for (UUID mencionado : evento.mencionados()) {
            novas.add(new Nova(mencionado, TipoNotificacao.MENCAO, autor + " te marcou em " + ref,
                    resumo(card, evento.trecho()), link));
        }

        if (evento instanceof LiberacaoEvento.Criado) {
            for (UserEntity analista : userRepository.findByAnalistaTrue()) {
                novas.add(new Nova(analista.getId(), TipoNotificacao.CARD_CRIADO, autor + " abriu " + ref,
                        resumo(card, LiberacaoService.moeda(card.getValor())), link));
            }
        } else if (evento instanceof LiberacaoEvento.Movido movido) {
            novas.addAll(movimento(card, movido, autor, ref, link));
        } else if (evento instanceof LiberacaoEvento.ParecerDado parecer) {
            TipoNotificacao tipo = parecer.ultimo() ? TipoNotificacao.COMITE_COMPLETO : TipoNotificacao.PARECER_REGISTRADO;
            String titulo = parecer.ultimo()
                    ? "Comitê completo em " + ref + ": liberado para decidir"
                    : autor + (parecer.revisao() ? " reviu o parecer em " : " deu parecer em ") + ref;
            String posicao = parecer.parecer().getPosicao() == null ? "" : rotulo(parecer.parecer());
            comiteDaRodada(card).forEach(id -> novas.add(new Nova(id, tipo, titulo, resumo(card, posicao), link)));
        } else if (evento instanceof LiberacaoEvento.PendenciaAberta aberta) {
            novas.add(new Nova(aberta.pendencia().getDestinatarioId(), TipoNotificacao.PENDENCIA_ABERTA,
                    autor + " abriu uma pendência para você em " + ref, resumo(card, evento.trecho()), link));
        } else if (evento instanceof LiberacaoEvento.PendenciaRespondida respondida) {
            novas.add(new Nova(respondida.pendencia().getAbertaPorId(), TipoNotificacao.PENDENCIA_RESPONDIDA,
                    autor + " respondeu a pendência de " + ref, resumo(card, evento.trecho()), link));
        } else if (evento instanceof LiberacaoEvento.Comentado comentado && !comentado.edicao()) {
            membros(card).forEach(id -> novas.add(new Nova(id, TipoNotificacao.COMENTARIO, autor + " comentou em " + ref,
                    resumo(card, evento.trecho()), link)));
        }
        return novas;
    }

    private List<Nova> movimento(LiberacaoCardEntity card, LiberacaoEvento.Movido movido, String autor, String ref, String link) {
        List<Nova> novas = new ArrayList<>();
        EtapaLiberacao para = movido.para();
        if (para == EtapaLiberacao.COMITE) {
            boolean volta = movido.de() == EtapaLiberacao.PENDENCIA;
            String titulo = volta ? ref + " voltou ao Comitê" : ref + " chegou ao Comitê: seu parecer é esperado";
            TipoNotificacao tipo = volta ? TipoNotificacao.DECISAO : TipoNotificacao.PARECER_ESPERADO;
            comiteDaRodada(card).forEach(id -> novas.add(new Nova(id, tipo, titulo, resumo(card, null), link)));
            return novas;
        }
        String acao = switch (para) {
            case APROVADO -> "aprovado";
            case REPROVADO -> "reprovado";
            case ORIGEM -> "devolvido à Origem";
            default -> null;
        };
        if (acao != null) {
            String titulo = ref + " " + acao + " por " + autor;
            List<UUID> interessados = new ArrayList<>(membros(card));
            interessados.add(0, card.getCriadoPorId());
            interessados.stream().filter(Objects::nonNull).distinct()
                    .forEach(id -> novas.add(new Nova(id, TipoNotificacao.DECISAO, titulo, resumo(card, movido.trecho()), link)));
        }
        return novas;
    }

    private List<UUID> membros(LiberacaoCardEntity card) {
        return membroRepository.findByCardIdOrderByAdicionadoEm(card.getId()).stream()
                .map(LiberacaoMembroEntity::getUsuarioId)
                .toList();
    }

    private List<UUID> comiteDaRodada(LiberacaoCardEntity card) {
        return parecerRepository.findByCardIdAndRodadaOrderByCriadoEm(card.getId(), card.getRodada()).stream()
                .map(LiberacaoParecerEntity::getUsuarioId)
                .filter(Objects::nonNull)
                .toList();
    }

    public static String link(LiberacaoCardEntity card) {
        return "/liberacao?card=" + card.getId();
    }

    /** "ACME LTDA · trecho". O nome do cedente vem primeiro: é por ele que o time reconhece o card. */
    private static String resumo(LiberacaoCardEntity card, String detalhe) {
        if (detalhe == null || detalhe.isBlank() || "—".equals(detalhe)) {
            return card.getCedenteNome();
        }
        String limpo = detalhe.replaceAll("\\s+", " ").trim();
        return card.getCedenteNome() + " · " + (limpo.length() > 160 ? limpo.substring(0, 159) + "…" : limpo);
    }

    private static String primeiroNome(String nome) {
        return nome == null || nome.isBlank() ? "Alguém" : nome.trim().split("\\s+")[0];
    }

    private static String rotulo(LiberacaoParecerEntity parecer) {
        return switch (parecer.getPosicao()) {
            case FAVORAVEL -> "Favorável";
            case COM_RESSALVAS -> "Com ressalvas";
            case DESFAVORAVEL -> "Desfavorável";
        };
    }
}
