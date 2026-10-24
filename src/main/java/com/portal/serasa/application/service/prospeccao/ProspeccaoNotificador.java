package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.application.service.notificacao.NotificacaoService;
import com.portal.serasa.application.service.notificacao.NotificacaoService.Nova;
import com.portal.serasa.application.service.notificacao.SseHub;
import com.portal.serasa.domain.model.notificacao.TipoNotificacao;
import com.portal.serasa.infrastructure.email.LiberacaoEmail;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoComentarioEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoComentarioJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Decide quem fica sabendo de cada acontecimento da esteira de prospecção, depois do commit.
 *
 * <p>Tabela do design (2026-10-11, seção 4). Menção vem sempre primeiro: quem foi marcado recebe
 * "te marcou", e não um segundo aviso genérico. Sino, SSE e e-mail são os mesmos da esteira de
 * liberação, e a preferência de e-mail de cada pessoa vale para as duas.</p>
 *
 * <p>Avisos para o comercial já estão escritos — pendência aberta vai para o comercial do card
 * quando ele é usuário do portal — e passam a valer de fato na Fase B, quando ele tiver acesso.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProspeccaoNotificador {

    /** Evento do canal que faz o quadro de quem está olhando se atualizar. */
    public static final String EVENTO_QUADRO = "prospeccao";

    /** O que também vai por e-mail: o que pede ação da pessoa. Comentário genérico fica no sino. */
    static final Set<TipoNotificacao> POR_EMAIL = EnumSet.of(
            TipoNotificacao.CARD_CRIADO, TipoNotificacao.PENDENCIA_ABERTA, TipoNotificacao.PENDENCIA_RESPONDIDA,
            TipoNotificacao.MENCAO, TipoNotificacao.RESPOSTA_CLIENTE,
            TipoNotificacao.PARECER_ESPERADO, TipoNotificacao.COMITE_COMPLETO, TipoNotificacao.DECISAO,
            TipoNotificacao.DOCUMENTOS_COMPLETOS);

    private final NotificacaoService notificacaoService;
    private final SseHub hub;
    private final UserRepository userRepository;
    private final ProspeccaoComentarioJpaRepository comentarioRepository;
    private final com.portal.serasa.infrastructure.persistence.repository.ProspeccaoParecerJpaRepository parecerRepository;
    private final LiberacaoEmail email;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoAcontecer(ProspeccaoAcontecimento acontecimento) {
        if (!(acontecimento instanceof ProspeccaoAcontecimento.Alterado)) {
            try {
                List<Nova> novas = destinatarios(acontecimento);
                UserEntity autor = acontecimento.autor();
                if (!novas.isEmpty() && autor != null) {
                    var entregas = notificacaoService.notificar(novas, autor.getId(), autor.getName());
                    notificacaoService.entregar(entregas);
                    enviarEmails(entregas);
                }
            } catch (RuntimeException erro) {
                // A ação já foi gravada; falhar o aviso não pode virar erro para quem agiu.
                log.error("Falha ao notificar {} do card {}", acontecimento.getClass().getSimpleName(),
                        acontecimento.card().getId(), erro);
            }
        }
        hub.enviarTodos(EVENTO_QUADRO, Map.of("cardId", acontecimento.card().getId()));
    }

    List<Nova> destinatarios(ProspeccaoAcontecimento acontecimento) {
        ProspeccaoEntity card = acontecimento.card();
        String autor = primeiroNome(acontecimento.autor() == null ? null : acontecimento.autor().getName());
        String empresa = card.getRazaoSocial();
        String link = link(card);
        List<Nova> novas = new ArrayList<>();

        for (UUID mencionado : acontecimento.mencionados()) {
            novas.add(new Nova(mencionado, TipoNotificacao.MENCAO, autor + " te marcou na prospecção de " + empresa,
                    resumo(card, acontecimento.trecho()), link));
        }

        if (acontecimento instanceof ProspeccaoAcontecimento.Criado) {
            for (UserEntity analista : userRepository.findByAnalistaTrue()) {
                novas.add(new Nova(analista.getId(), TipoNotificacao.CARD_CRIADO, autor + " abriu prospecção: " + empresa,
                        resumo(card, card.getComercialNome() == null ? null : "comercial " + card.getComercialNome()), link));
            }
        } else if (acontecimento instanceof ProspeccaoAcontecimento.PendenciaAberta) {
            if (card.getComercialId() != null) {
                novas.add(new Nova(card.getComercialId(), TipoNotificacao.PENDENCIA_ABERTA,
                        autor + " abriu uma pendência em " + empresa, resumo(card, acontecimento.trecho()), link));
            }
        } else if (acontecimento instanceof ProspeccaoAcontecimento.PendenciaRespondida respondida) {
            novas.add(new Nova(respondida.pendencia().getAbertaPor(), TipoNotificacao.PENDENCIA_RESPONDIDA,
                    autor + " respondeu a pendência de " + empresa, resumo(card, acontecimento.trecho()), link));
        } else if (acontecimento instanceof ProspeccaoAcontecimento.AprovadoNaAnalise) {
            // O comercial começa a juntar os documentos do cliente.
            if (card.getComercialId() != null) {
                novas.add(new Nova(card.getComercialId(), TipoNotificacao.DECISAO,
                        empresa + " aprovada na análise: junte os documentos", resumo(card, acontecimento.trecho()), link));
            }
        } else if (acontecimento instanceof ProspeccaoAcontecimento.DocumentosCompletos) {
            if (card.getAnalistaId() != null) {
                novas.add(new Nova(card.getAnalistaId(), TipoNotificacao.DOCUMENTOS_COMPLETOS,
                        "Documentos de " + empresa + " completos: confira", resumo(card, null), link));
            }
        } else if (acontecimento instanceof ProspeccaoAcontecimento.RespostaCliente resposta) {
            String verbo = switch (resposta.resposta()) {
                case ACEITO -> "aceitou a proposta";
                case RECUSADO -> "recusou a proposta";
                case CONTRAPROPOSTA -> "fez contraproposta";
            };
            novas.add(new Nova(card.getAnalistaId(), TipoNotificacao.RESPOSTA_CLIENTE,
                    empresa + " " + verbo, resumo(card, acontecimento.trecho()), link));
        } else if (acontecimento instanceof ProspeccaoAcontecimento.ComiteConvocado convocado) {
            for (UUID membro : convocado.membros()) {
                novas.add(new Nova(membro, TipoNotificacao.PARECER_ESPERADO,
                        empresa + " chegou ao comitê: seu parecer é esperado", resumo(card, null), link));
            }
        } else if (acontecimento instanceof ProspeccaoAcontecimento.ParecerDado dado) {
            if (dado.ultimo()) {
                novas.add(new Nova(card.getAnalistaId(), TipoNotificacao.COMITE_COMPLETO,
                        "Comitê completo em " + empresa + ": pode registrar o resultado", resumo(card, null), link));
            }
            String titulo = autor + (dado.revisao() ? " reviu o parecer em " : " deu parecer em ") + empresa;
            String posicao = dado.parecer().getPosicao() == null ? null
                    : ProspeccaoComiteService.rotulo(dado.parecer().getPosicao());
            novas.add(new Nova(card.getAnalistaId(), TipoNotificacao.PARECER_REGISTRADO, titulo, resumo(card, posicao), link));
            parecerRepository.findByProspeccaoIdAndRodadaOrderByCriadoEmAsc(card.getId(), card.getRodadaComite()).stream()
                    .map(com.portal.serasa.infrastructure.persistence.entity.ProspeccaoParecerEntity::getUsuarioId)
                    .filter(Objects::nonNull)
                    .forEach(id -> novas.add(new Nova(id, TipoNotificacao.PARECER_REGISTRADO, titulo, resumo(card, posicao), link)));
        } else if (acontecimento instanceof ProspeccaoAcontecimento.ComiteDecidiu decidiu) {
            String titulo = switch (decidiu.resultado()) {
                case APROVADO -> empresa + " aprovada no comitê";
                case DEVOLVIDO -> empresa + " devolvida pelo comitê para a análise";
                case REPROVADO -> empresa + " reprovada no comitê";
            };
            List<UUID> interessados = new ArrayList<>();
            interessados.add(card.getAnalistaId());
            interessados.addAll(decidiu.membros());
            // Aprovado no comitê, a proposta vai ao cliente: o comercial colhe o aceite.
            if (decidiu.resultado() == com.portal.serasa.domain.model.prospeccao.ResultadoComite.APROVADO) {
                interessados.add(card.getComercialId());
            }
            interessados.stream().filter(Objects::nonNull).distinct()
                    .forEach(id -> novas.add(new Nova(id, TipoNotificacao.DECISAO, titulo, resumo(card, acontecimento.trecho()), link)));
        } else if (acontecimento instanceof ProspeccaoAcontecimento.Concluido) {
            // Fase B: o comercial com acesso fica sabendo que o cliente dele está habilitado.
            if (card.getComercialId() != null) {
                novas.add(new Nova(card.getComercialId(), TipoNotificacao.DECISAO, empresa + " habilitada", resumo(card, null), link));
            }
        } else if (acontecimento instanceof ProspeccaoAcontecimento.Cancelado cancelado) {
            // O comercial para de colher documento ou aceite; a dona fica sabendo se foi o admin;
            // no comitê, os membros da rodada param de esperar o próprio parecer.
            List<UUID> interessados = new ArrayList<>();
            interessados.add(card.getComercialId());
            interessados.add(card.getAnalistaId());
            interessados.addAll(cancelado.comite());
            interessados.stream().filter(Objects::nonNull).distinct()
                    .forEach(id -> novas.add(new Nova(id, TipoNotificacao.DECISAO,
                            empresa + " reprovada depois da aprovação", resumo(card, acontecimento.trecho()), link)));
        } else if (acontecimento instanceof ProspeccaoAcontecimento.Comentado comentado && !comentado.edicao()) {
            for (UUID interessado : interessados(card)) {
                novas.add(new Nova(interessado, TipoNotificacao.COMENTARIO, autor + " comentou em " + empresa,
                        resumo(card, acontecimento.trecho()), link));
            }
        }
        return novas;
    }

    /** Quem acompanha a conversa: a analista dona e quem já comentou no card. */
    private Set<UUID> interessados(ProspeccaoEntity card) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (card.getAnalistaId() != null) {
            ids.add(card.getAnalistaId());
        }
        comentarioRepository.findByProspeccaoIdAndExcluidoEmIsNullOrderByCriadoEmDesc(card.getId()).stream()
                .map(ProspeccaoComentarioEntity::getAutorId)
                .filter(Objects::nonNull)
                .forEach(ids::add);
        return ids;
    }

    private void enviarEmails(List<NotificacaoService.Entrega> entregas) {
        List<NotificacaoService.Entrega> porEmail = entregas.stream()
                .filter(entrega -> POR_EMAIL.contains(entrega.notificacao().tipo()))
                .toList();
        if (porEmail.isEmpty()) {
            return;
        }
        Map<UUID, UserEntity> pessoas = new HashMap<>();
        userRepository.findAllById(porEmail.stream().map(NotificacaoService.Entrega::destinatarioId).toList())
                .forEach(user -> pessoas.put(user.getId(), user));
        for (NotificacaoService.Entrega entrega : porEmail) {
            UserEntity pessoa = pessoas.get(entrega.destinatarioId());
            if (pessoa != null && pessoa.isEmailLiberacao()) {
                email.enviar(pessoa.getEmail(), pessoa.getName(), entrega.notificacao().titulo(),
                        entrega.notificacao().resumo(), entrega.notificacao().link());
            }
        }
    }

    public static String link(ProspeccaoEntity card) {
        return "/prospeccao?card=" + card.getId();
    }

    private static String resumo(ProspeccaoEntity card, String detalhe) {
        if (detalhe == null || detalhe.isBlank()) {
            return card.getRazaoSocial();
        }
        String limpo = detalhe.replaceAll("\\s+", " ").trim();
        return card.getRazaoSocial() + " · " + (limpo.length() > 160 ? limpo.substring(0, 159) + "…" : limpo);
    }

    private static String primeiroNome(String nome) {
        return nome == null || nome.isBlank() ? "Alguém" : nome.trim().split("\\s+")[0];
    }
}
