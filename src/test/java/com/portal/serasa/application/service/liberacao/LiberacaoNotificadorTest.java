package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.application.service.notificacao.NotificacaoService;
import com.portal.serasa.application.service.notificacao.NotificacaoService.Entrega;
import com.portal.serasa.application.service.notificacao.NotificacaoService.Item;
import com.portal.serasa.application.service.notificacao.NotificacaoService.Nova;
import com.portal.serasa.application.service.notificacao.SseHub;
import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import com.portal.serasa.domain.model.liberacao.OrigemMembro;
import com.portal.serasa.domain.model.liberacao.PosicaoParecer;
import com.portal.serasa.domain.model.liberacao.ResultadoLiberacao;
import com.portal.serasa.domain.model.notificacao.TipoNotificacao;
import com.portal.serasa.infrastructure.email.LiberacaoEmail;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoCardEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoComentarioEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoMembroEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoParecerEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoPendenciaEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoMembroJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoParecerJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LiberacaoNotificadorTest {

    @Mock private NotificacaoService notificacaoService;
    @Mock private SseHub hub;
    @Mock private LiberacaoMembroJpaRepository membroRepository;
    @Mock private LiberacaoParecerJpaRepository parecerRepository;
    @Mock private UserRepository userRepository;
    @Mock private LiberacaoEmail email;

    @InjectMocks private LiberacaoNotificador notificador;

    private UserEntity aline;
    private UserEntity andressa;
    private UserEntity mychelly;
    private UserEntity diretoria;
    private LiberacaoCardEntity card;

    @BeforeEach
    void setUp() {
        aline = usuario("Aline Souza", false, false);
        andressa = usuario("Andressa Lima", true, true);
        mychelly = usuario("Mychelly Costa", true, true);
        diretoria = usuario("Diretoria", true, false);
        card = LiberacaoCardEntity.builder().id(UUID.randomUUID()).numero(42L).etapa(EtapaLiberacao.COMITE).rodada(1)
                .cedenteNome("ACME LTDA").valor(new BigDecimal("80000")).criadoPorId(aline.getId()).build();
        when(userRepository.findByAnalistaTrue()).thenReturn(List.of(andressa, mychelly, diretoria));
        when(userRepository.findAllById(any())).thenAnswer(inv -> {
            java.util.Collection<UUID> ids = inv.getArgument(0);
            return List.of(aline, andressa, mychelly, diretoria).stream().filter(user -> ids.contains(user.getId())).toList();
        });
        // Mesma regra do serviço real: sem o autor e uma notificação por pessoa, a primeira da lista vence.
        when(notificacaoService.notificar(anyCollection(), any(), any())).thenAnswer(inv -> {
            java.util.Collection<Nova> novas = inv.getArgument(0);
            UUID autorId = inv.getArgument(1);
            Map<UUID, Nova> porPessoa = new LinkedHashMap<>();
            novas.stream().filter(nova -> !nova.destinatarioId().equals(autorId))
                    .forEach(nova -> porPessoa.putIfAbsent(nova.destinatarioId(), nova));
            return porPessoa.values().stream()
                    .map(nova -> new Entrega(nova.destinatarioId(), new Item(UUID.randomUUID(), nova.tipo(), nova.titulo(),
                            nova.resumo(), nova.link(), "Alguém", LocalDateTime.now(), null), 1L))
                    .toList();
        });
        when(parecerRepository.findByCardIdAndRodadaOrderByCriadoEm(card.getId(), 1)).thenReturn(List.of(
                parecer(andressa, PosicaoParecer.FAVORAVEL), parecer(mychelly, null)));
        when(membroRepository.findByCardIdOrderByAdicionadoEm(card.getId())).thenReturn(List.of(
                membro(aline), membro(andressa), membro(mychelly)));
    }

    private UserEntity usuario(String nome, boolean analista, boolean comite) {
        return UserEntity.builder().id(UUID.randomUUID()).name(nome).email(nome.split(" ")[0].toLowerCase() + "@jgm.com")
                .analista(analista).comite(comite).build();
    }

    private LiberacaoParecerEntity parecer(UserEntity user, PosicaoParecer posicao) {
        return LiberacaoParecerEntity.builder().cardId(card.getId()).rodada(1).usuarioId(user.getId())
                .usuarioNome(user.getName()).posicao(posicao).build();
    }

    private LiberacaoMembroEntity membro(UserEntity user) {
        return LiberacaoMembroEntity.builder().cardId(card.getId()).usuarioId(user.getId())
                .origem(OrigemMembro.MANUAL).adicionadoEm(LocalDateTime.now()).build();
    }

    private Map<UUID, TipoNotificacao> porPessoa(List<Nova> novas) {
        return novas.stream().collect(java.util.stream.Collectors.toMap(Nova::destinatarioId, Nova::tipo, (a, b) -> a));
    }

    @Test
    @DisplayName("card criado: avisa todas as analistas, Diretoria inclusive")
    void shouldNotifyAnalystsOnCreate() {
        card.setEtapa(EtapaLiberacao.ORIGEM);
        List<Nova> novas = notificador.destinatarios(new LiberacaoEvento.Criado(card, aline, Set.of(), null));
        assertThat(porPessoa(novas)).containsOnlyKeys(andressa.getId(), mychelly.getId(), diretoria.getId())
                .containsValue(TipoNotificacao.CARD_CRIADO);
        assertThat(novas.get(0).titulo()).isEqualTo("Aline abriu #42");
        assertThat(novas.get(0).link()).isEqualTo("/liberacao?card=" + card.getId());
    }

    @Test
    @DisplayName("entrou no Comitê: o Comitê da rodada recebe 'seu parecer é esperado'")
    void shouldAskComiteForParecer() {
        List<Nova> novas = notificador.destinatarios(
                new LiberacaoEvento.Movido(card, EtapaLiberacao.ORIGEM, EtapaLiberacao.COMITE, aline, Set.of(), null));
        assertThat(porPessoa(novas)).containsOnlyKeys(andressa.getId(), mychelly.getId());
        assertThat(novas).allMatch(nova -> nova.tipo() == TipoNotificacao.PARECER_ESPERADO);
    }

    @Test
    @DisplayName("finalizado aprovado: criador e membros recebem a decisão, com o resultado no título")
    void shouldNotifyDecision() {
        card.setEtapa(EtapaLiberacao.FINALIZADO);
        card.setResultado(ResultadoLiberacao.APROVADO);
        List<Nova> novas = notificador.destinatarios(
                new LiberacaoEvento.Movido(card, EtapaLiberacao.COMITE, EtapaLiberacao.FINALIZADO, andressa, Set.of(), null));
        assertThat(porPessoa(novas)).containsKeys(aline.getId(), mychelly.getId());
        assertThat(novas.get(0).titulo()).isEqualTo("#42 finalizado (aprovado) por Andressa");
        assertThat(novas).allMatch(nova -> nova.tipo() == TipoNotificacao.DECISAO);
    }

    @Test
    @DisplayName("finalizado reprovado: título traz '(reprovado)'")
    void shouldNotifyRejectedDecision() {
        card.setEtapa(EtapaLiberacao.FINALIZADO);
        card.setResultado(ResultadoLiberacao.REPROVADO);
        List<Nova> novas = notificador.destinatarios(
                new LiberacaoEvento.Movido(card, EtapaLiberacao.PENDENCIA, EtapaLiberacao.FINALIZADO, andressa, Set.of(), null));
        assertThat(novas).isNotEmpty()
                .allMatch(nova -> nova.titulo().equals("#42 finalizado (reprovado) por Andressa"));
    }

    @Test
    @DisplayName("finalizado parcial: título traz '(parcialmente aprovado)'")
    void shouldNotifyPartialDecision() {
        card.setEtapa(EtapaLiberacao.FINALIZADO);
        card.setResultado(ResultadoLiberacao.PARCIAL);
        List<Nova> novas = notificador.destinatarios(
                new LiberacaoEvento.Movido(card, EtapaLiberacao.COMITE, EtapaLiberacao.FINALIZADO, andressa, Set.of(), null));
        assertThat(novas.get(0).titulo()).isEqualTo("#42 finalizado (parcialmente aprovado) por Andressa");
    }

    @Test
    @DisplayName("finalizado sem resultado gravado: título sem parênteses")
    void shouldNotifyFinalizadoWithoutResultado() {
        card.setEtapa(EtapaLiberacao.FINALIZADO);
        card.setResultado(null);
        List<Nova> novas = notificador.destinatarios(
                new LiberacaoEvento.Movido(card, EtapaLiberacao.COMITE, EtapaLiberacao.FINALIZADO, andressa, Set.of(), null));
        assertThat(novas.get(0).titulo()).isEqualTo("#42 finalizado por Andressa");
    }

    @Test
    @DisplayName("devolvido à Origem: criador e membros recebem 'devolvido à Origem'")
    void shouldNotifyReturnToOrigem() {
        card.setEtapa(EtapaLiberacao.ORIGEM);
        List<Nova> novas = notificador.destinatarios(
                new LiberacaoEvento.Movido(card, EtapaLiberacao.COMITE, EtapaLiberacao.ORIGEM, andressa, Set.of(), null));
        assertThat(novas.get(0).titulo()).isEqualTo("#42 devolvido à Origem por Andressa");
    }

    @Test
    @DisplayName("reaberto de Finalizado para o Comitê: o Comitê da rodada é chamado de novo")
    void shouldAskComiteAgainWhenReopened() {
        List<Nova> novas = notificador.destinatarios(
                new LiberacaoEvento.Movido(card, EtapaLiberacao.FINALIZADO, EtapaLiberacao.COMITE, andressa, Set.of(), null));
        assertThat(porPessoa(novas)).containsOnlyKeys(andressa.getId(), mychelly.getId());
        assertThat(novas).allMatch(nova -> nova.tipo() == TipoNotificacao.PARECER_ESPERADO);
    }

    @Test
    @DisplayName("último parecer: Comitê completo; parecer intermediário: parecer registrado")
    void shouldDistinguishLastParecer() {
        LiberacaoParecerEntity dado = parecer(mychelly, PosicaoParecer.COM_RESSALVAS);
        assertThat(notificador.destinatarios(new LiberacaoEvento.ParecerDado(card, dado, true, false, mychelly, Set.of(), null)))
                .allMatch(nova -> nova.tipo() == TipoNotificacao.COMITE_COMPLETO);
        assertThat(notificador.destinatarios(new LiberacaoEvento.ParecerDado(card, dado, false, false, mychelly, Set.of(), null)))
                .allMatch(nova -> nova.tipo() == TipoNotificacao.PARECER_REGISTRADO);
    }

    @Test
    @DisplayName("pendência aberta avisa o destinatário; respondida avisa quem abriu")
    void shouldRoutePendencia() {
        LiberacaoPendenciaEntity pendencia = LiberacaoPendenciaEntity.builder()
                .abertaPorId(andressa.getId()).destinatarioId(aline.getId()).texto("Confirmar aceite").build();
        assertThat(porPessoa(notificador.destinatarios(new LiberacaoEvento.PendenciaAberta(card, pendencia, andressa, Set.of(), "Confirmar aceite"))))
                .containsExactly(Map.entry(aline.getId(), TipoNotificacao.PENDENCIA_ABERTA));
        assertThat(porPessoa(notificador.destinatarios(new LiberacaoEvento.PendenciaRespondida(card, pendencia, aline, Set.of(), "Ok"))))
                .containsExactly(Map.entry(andressa.getId(), TipoNotificacao.PENDENCIA_RESPONDIDA));
    }

    @Test
    @DisplayName("comentário com menção: marcado recebe MENCAO antes do aviso de comentário")
    void shouldPutMentionFirst() {
        LiberacaoComentarioEntity comentario = LiberacaoComentarioEntity.builder().texto("x").build();
        List<Nova> novas = notificador.destinatarios(
                new LiberacaoEvento.Comentado(card, comentario, false, andressa, Set.of(aline.getId()), "Aline, confere?"));
        assertThat(novas.get(0)).satisfies(nova -> {
            assertThat(nova.destinatarioId()).isEqualTo(aline.getId());
            assertThat(nova.tipo()).isEqualTo(TipoNotificacao.MENCAO);
            assertThat(nova.titulo()).isEqualTo("Andressa te marcou em #42");
            assertThat(nova.resumo()).isEqualTo("ACME LTDA · Aline, confere?");
        });
        assertThat(novas).anyMatch(nova -> nova.destinatarioId().equals(mychelly.getId()) && nova.tipo() == TipoNotificacao.COMENTARIO);
    }

    @Test
    @DisplayName("comentário editado só avisa quem passou a ser marcado")
    void shouldNotNotifyMembersOnEdit() {
        LiberacaoComentarioEntity comentario = LiberacaoComentarioEntity.builder().texto("x").build();
        assertThat(notificador.destinatarios(new LiberacaoEvento.Comentado(card, comentario, true, andressa, Set.of(), null))).isEmpty();
    }

    // ------------------------------------------------------------------ e-mail

    private static LiberacaoEvento.Comentado comentario(LiberacaoCardEntity card, UserEntity autor, Set<UUID> marcados) {
        return new LiberacaoEvento.Comentado(card, LiberacaoComentarioEntity.builder().texto("x").build(), false,
                autor, marcados, "Aline, confere?");
    }

    @Test
    @DisplayName("e-mail: só os tipos dirigidos à pessoa vão por e-mail; comentário e parecer dos outros ficam no sino")
    void shouldOnlyEmailPersonalNotificationTypes() {
        assertThat(LiberacaoNotificador.POR_EMAIL).contains(TipoNotificacao.MENCAO, TipoNotificacao.CARD_CRIADO,
                TipoNotificacao.PARECER_ESPERADO, TipoNotificacao.COMITE_COMPLETO, TipoNotificacao.PENDENCIA_ABERTA,
                TipoNotificacao.PENDENCIA_RESPONDIDA, TipoNotificacao.DECISAO);
        assertThat(LiberacaoNotificador.POR_EMAIL).doesNotContain(TipoNotificacao.COMENTARIO,
                TipoNotificacao.PARECER_REGISTRADO);
    }

    @Test
    @DisplayName("e-mail: menção para quem tem e-mail da esteira ligado envia com título, resumo e link")
    void shouldEmailMentionedUserWhenEmailIsOn() {
        aline.setEmailLiberacao(true);

        notificador.aoAcontecer(comentario(card, andressa, Set.of(aline.getId())));

        verify(email).enviar("aline@jgm.com", "Aline Souza", "Andressa te marcou em #42",
                "ACME LTDA · Aline, confere?", "/liberacao?card=" + card.getId());
    }

    @Test
    @DisplayName("e-mail: menção para quem desligou o e-mail da esteira não envia, mas o sino continua")
    void shouldNotEmailMentionedUserWhenEmailIsOff() {
        aline.setEmailLiberacao(false);

        notificador.aoAcontecer(comentario(card, andressa, Set.of(aline.getId())));

        verify(email, never()).enviar(any(), any(), any(), any(), any());
        verify(notificacaoService).entregar(any());
    }

    @Test
    @DisplayName("e-mail: comentário (sem menção) não vai por e-mail, mesmo para quem tem o e-mail ligado")
    void shouldNotEmailComments() {
        notificador.aoAcontecer(comentario(card, andressa, Set.of()));

        verify(notificacaoService).entregar(argThat(entregas -> !entregas.isEmpty()
                && entregas.stream().allMatch(entrega -> entrega.notificacao().tipo() == TipoNotificacao.COMENTARIO)));
        verify(email, never()).enviar(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("e-mail: parecer intermediário não vai por e-mail; o que completa o Comitê vai")
    void shouldEmailOnlyTheParecerThatCompletesTheComite() {
        LiberacaoParecerEntity dado = parecer(mychelly, PosicaoParecer.COM_RESSALVAS);

        notificador.aoAcontecer(new LiberacaoEvento.ParecerDado(card, dado, false, false, mychelly, Set.of(), null));
        verify(email, never()).enviar(any(), any(), any(), any(), any());

        notificador.aoAcontecer(new LiberacaoEvento.ParecerDado(card, dado, true, false, mychelly, Set.of(), null));
        verify(email).enviar(eq("andressa@jgm.com"), eq("Andressa Lima"),
                eq("Comitê completo em #42: liberado para decidir"), any(), any());
    }

    @Test
    @DisplayName("e-mail: decisão (finalizado) vai para o criador do card que tem o e-mail ligado")
    void shouldEmailDecisionToCreator() {
        card.setEtapa(EtapaLiberacao.FINALIZADO);
        card.setResultado(ResultadoLiberacao.REPROVADO);

        notificador.aoAcontecer(
                new LiberacaoEvento.Movido(card, EtapaLiberacao.COMITE, EtapaLiberacao.FINALIZADO, andressa, Set.of(), null));

        verify(email).enviar(eq("aline@jgm.com"), eq("Aline Souza"),
                eq("#42 finalizado (reprovado) por Andressa"), any(), any());
        // o autor da decisão nunca é avisado do que ele mesmo fez
        verify(email, never()).enviar(eq("andressa@jgm.com"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("e-mail: destinatário que não existe mais é ignorado sem derrubar o aviso do quadro")
    void shouldSkipEmailForUnknownRecipient() {
        UUID removido = UUID.randomUUID();

        notificador.aoAcontecer(comentario(card, andressa, Set.of(removido)));

        verify(email, never()).enviar(any(), any(), any(), any(), any());
        verify(hub).enviarTodos(eq(LiberacaoNotificador.EVENTO_QUADRO), any());
    }

    @Test
    @DisplayName("e-mail: falha ao enviar não impede o aviso de quadro alterado")
    void shouldBroadcastBoardEvenWhenEmailFails() {
        doThrow(new RuntimeException("smtp fora")).when(email).enviar(any(), any(), any(), any(), any());

        notificador.aoAcontecer(comentario(card, andressa, Set.of(aline.getId())));

        verify(hub).enviarTodos(eq(LiberacaoNotificador.EVENTO_QUADRO), any());
    }

    @Test
    @DisplayName("falha ao notificar não impede o aviso de quadro alterado")
    void shouldBroadcastBoardEvenWhenNotificationFails() {
        doThrow(new RuntimeException("banco fora")).when(notificacaoService).notificar(anyCollection(), any(), any());
        notificador.aoAcontecer(new LiberacaoEvento.Criado(card, aline, Set.of(), null));
        verify(hub).enviarTodos(eq(LiberacaoNotificador.EVENTO_QUADRO), any());
    }
}
